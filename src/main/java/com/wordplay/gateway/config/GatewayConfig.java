package com.wordplay.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.wordplay.gateway.handler.JsonSentinelGatewayBlockExceptionHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.result.view.ViewResolver;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * gateway sentinel配置
 *
 * @author zhuangpf
 */
@Configuration
public class GatewayConfig {

	private final List<ViewResolver> viewResolvers;
	private final ServerCodecConfigurer serverCodecConfigurer;

	public GatewayConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider, ServerCodecConfigurer serverCodecConfigurer) {
		this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
		this.serverCodecConfigurer = serverCodecConfigurer;
	}

	/**
	 * 配置限流的异常处理器:SentinelGatewayBlockExceptionHandler
	 * 自定义异常提示：当发生限流、熔断异常时，会返回定义的提示信息。
	 */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public JsonSentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
		// Register the block exception handler for Spring Cloud Gateway.
		return new JsonSentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
	}

	/**
	 * 由于sentinel的工作原理其实借助于全局的filter进行请求拦截并计算出是否进行限流、熔断等操作的，增加SentinelGateWayFilter配置。
	 */
	@Bean
	@Order(Ordered.HIGHEST_PRECEDENCE)
	public GlobalFilter sentinelGatewayFilter() {
		return new SentinelGatewayFilter();
	}

	/**
	 * sentinel 不仅支持通过硬代码方式进行资源的申明，还能通过注解方式进行声明，
	 * 为了让注解生效，还需要配置切面类SentinelResourceAspect
	 */
	@Bean
	public SentinelResourceAspect sentinelResourceAspect() {
		return new SentinelResourceAspect();
	}

	@PostConstruct
	public void doInit() {
		initGatewayRules();
	}

	/**
	 * 配置限流规则
	 */
	private void initGatewayRules() {
		Set<GatewayFlowRule> rules = new HashSet<>();
		//order-center 路由ID，和配置文件中的一致就行
		rules.add(new GatewayFlowRule("order-center")
				.setCount(1) // 限流阈值
				.setIntervalSec(1) // 统计时间窗口，单位是秒，默认是 1 秒
		);
		GatewayRuleManager.loadRules(rules);
	}

}
