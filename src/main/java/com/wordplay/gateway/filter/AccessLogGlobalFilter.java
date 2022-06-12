package com.wordplay.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.wordplay.gateway.entity.AccessLog;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author zhuangpf
 */
@Slf4j
@Component
public class AccessLogGlobalFilter implements GlobalFilter, Ordered {

	private Logger LOGGER = LoggerFactory.getLogger(AccessLogGlobalFilter.class);

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		// 缓存请求参数，解决body只能读一次问题
		RecorderServerHttpRequestDecorator requestDecorator = new RecorderServerHttpRequestDecorator(request);
		// 请求机器的地址
		InetSocketAddress address = requestDecorator.getRemoteAddress();
		// 请求方法
		HttpMethod method = requestDecorator.getMethod();
		// 请求URL
		URI url = requestDecorator.getURI();
		// 请求头
		HttpHeaders headers = requestDecorator.getHeaders();
		// 读取requestBody传参
		Flux<DataBuffer> body = requestDecorator.getBody();
		AtomicReference<String> requestBody = new AtomicReference<>("");
		body.subscribe(buffer -> {
			CharBuffer charBuffer = StandardCharsets.UTF_8.decode(buffer.asByteBuffer());
			requestBody.set(charBuffer.toString());
		});
		String reqBody = requestBody.get();
		// 【请求日志】
		AccessLog accessLog = new AccessLog();
		accessLog.setRequestTime(new Date());
		AccessLog.RequestLog requestLog = new AccessLog.RequestLog();
		requestLog.setHeaders(String.valueOf(headers));
		requestLog.setRequestBody(reqBody);
		requestLog.setMethod(method.name());
		requestLog.setAddress(address.getHostName() + address.getPort());
		requestLog.setUrl(url.getPath());
		accessLog.setRequestLog(requestLog);

		// 【响应日志】
		ServerHttpResponse response = exchange.getResponse();
		DataBufferFactory bufferFactory = response.bufferFactory();
		ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(response) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				// 响应时间 & 执行时间
				accessLog.setResponseTime(new Date());
				accessLog.setExecuteTime(System.currentTimeMillis() - accessLog.getRequestTime().getTime());
				if (body instanceof Flux) {
					Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
					return super.writeWith(fluxBody.map(dataBuffer -> {
						// probably should reuse buffers
						byte[] content = new byte[dataBuffer.readableByteCount()];
						dataBuffer.read(content);
						String responseResult = new String(content, Charset.forName("UTF-8"));
						AccessLog.ResponseLog responseLog = new AccessLog.ResponseLog();
						responseLog.setStatusCode(this.getStatusCode().toString());
						responseLog.setHeaders(String.valueOf(this.getHeaders()));
						responseLog.setResponseResult(responseResult);
						accessLog.setResponseLog(responseLog);
						LOGGER.info(JSON.toJSONString(accessLog));
						return bufferFactory.wrap(content);
					}));
				}
				// if body is not a flux. never got there.
				return super.writeWith(body);
			}
		};
		return chain.filter(exchange.mutate().request(requestDecorator).response(decoratedResponse).build());
	}

	/**
	 * 设定过滤器的优先级，值越小则优先级越高
	 */
	@Override
	public int getOrder() {
		return -2;
	}

}
