package com.wordplay.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 限流配置类。超出限流的url请求都将返回429(Too Many Requests)状态。<br/>
 * 使用 gateway 自身的filters 配置实现，<br/>
 * <p>
 * filters:<br/>
 * - name: RequestRateLimiter<br/>
 * args:<br/>
 * redis-rate-limiter.replenishRate: 10    # 令牌桶每秒填充速率<br/>
 * redis-rate-limiter.burstCapacity: 100   # 令牌桶总容量<br/>
 * key-resolver: '#{@ipKeyResolver}'   	   # 使用 SpEL 表达式按名称引用 bean
 *
 * @author zhuangpf
 */
@Configuration
public class RequestRateLimiterConfig {

	/**
	 * 按URL限流，即以每秒内请求数按URL分组统计，超出限流的url请求都将返回429(Too Many Requests)状态
	 */
	@Bean
	@Primary
	public KeyResolver apiKeyResolver() {
		// 按URL限流
		return exchange -> Mono.just(exchange.getRequest().getPath().toString());
	}

	/**
	 * 这里根据用户ID限流，请求路径中必须携带userId参数
	 */
	@Bean
	public KeyResolver userKeyResolver() {
		// 按用户限流
		return exchange -> Mono.just(exchange.getRequest().getQueryParams().getFirst("userId"));
	}

	/**
	 * 通过exchange对象可以获取到请求信息，这边用了HostName
	 */
	@Bean
	public KeyResolver ipKeyResolver() {
		// 按IP来限流
		return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getHostName());
	}

}
