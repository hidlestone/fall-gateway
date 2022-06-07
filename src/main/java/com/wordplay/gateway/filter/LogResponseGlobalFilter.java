package com.wordplay.gateway.filter;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.Charset;

/**
 * @author zhuangpf
 */
@Component
public class LogResponseGlobalFilter implements GlobalFilter, Ordered {

	private Logger log = LoggerFactory.getLogger(LogResponseGlobalFilter.class);

	private static final String REQUEST_PREFIX = "Request Info [ ";
	private static final String REQUEST_TAIL = " ]";
	private static final String RESPONSE_PREFIX = "Response Info [ ";
	private static final String RESPONSE_TAIL = " ]";
	private StringBuilder normalMsg = new StringBuilder();

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
		ServerHttpRequest request = exchange.getRequest();
		ServerHttpResponse response = exchange.getResponse();
		DataBufferFactory bufferFactory = response.bufferFactory();
		normalMsg.append(RESPONSE_PREFIX);
		ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(response) {
			@Override
			public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
				if (body instanceof Flux) {
					Flux<? extends DataBuffer> fluxBody = (Flux<? extends DataBuffer>) body;
					return super.writeWith(fluxBody.map(dataBuffer -> {
						// probably should reuse buffers
						byte[] content = new byte[dataBuffer.readableByteCount()];
						dataBuffer.read(content);
						String responseResult = new String(content, Charset.forName("UTF-8"));
						normalMsg.append("status=").append(this.getStatusCode());
						normalMsg.append(";header=").append(this.getHeaders());
						normalMsg.append(";responseResult=").append(responseResult);
						normalMsg.append(RESPONSE_TAIL);
						log.info(normalMsg.toString());
						return bufferFactory.wrap(content);
					}));
				}
				return super.writeWith(body); // if body is not a flux. never got there.
			}
		};
		return chain.filter(exchange.mutate().response(decoratedResponse).build());
	}

	@Override
	public int getOrder() {
		return -2;
	}

}
