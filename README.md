# fall-gateway

主要实现的功能：熔断、降级、限流

## 一、SpringCloudGateway 限流方案
在分布式系统中，网关作为流量的入口，因此会有大量的请求进入网关，向其他服务发起调用，其他服务不可避免的会出现调用失败（超时、异常），失败时不能让请求堆积在网关上，需要快速失败并返回给客户端，想要实现这个要求，就必须在网关上做熔断、降级操作。

在Spring Cloud Gateway中，有Filter过滤器，因此可以在“pre”类型的Filter中自行实现过滤器。但是限流作为网关最基本的功能，Spring Cloud Gateway官方就提供了RequestRateLimiterGatewayFilterFactory这个类，适用在Redis内的通过执行Lua脚本实现了令牌桶的方式。具体实现逻辑在RequestRateLimiterGatewayFilterFactory类中，lua脚本在如下图所示的文件夹中：
```
spring-cloud-gateway-core-2.2.2.RELEASE.jar!/META-INF/scripts/request_rate_limiter.lua
```
Spring Cloud Gateway 默认实现 Redis限流，如果扩展只需要实现Ratelimter接口即可，同时也可以通过自定义KeyResolver来指定限流的Key，比如我们需要根据用户、IP、URI来做限流等等，通过exchange对象可以获取到请求信息。    

引入redis-reactive依赖
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>

<!--springboot2.X默认使用lettuce连接池，需要引入commons-pool2-->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

## 二、整合 Actuator 
actuator健康检查配置，为之后的功能提供支持，此部分比较简单，不再赘述，加入以下maven依赖和配置。
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
配置
```yaml
# actuator 监控配置
management:
  # actuator端口 如果不配置做默认使用上面8080端口
  #server:
    #port: 8080
  endpoints:
    web:
      exposure:
        # 默认值访问health,info端点  用*可以包含全部端点
        include: "*"
      # 修改访问路径 2.0之前默认是/; 2.0默认是/actuator可以通过这个属性值修改
      base-path: /actuator
  endpoint:
    shutdown:
      enabled: true # 打开shutdown端点
    health:
      show-details: always # 获得健康检查中所有指标的详细信息
```

## 三、统一配置跨域请求
现在的请求通过经过gateWay网关时，需要在网关统一配置跨域请求，需求所有请求通过。
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowed-origins: "*"
            allowed-headers: "*"
            allow-credentials: true
            allowed-methods:
              - GET
              - POST
              - DELETE
              - PUT
              - OPTION
```

## 四、整合Sentinel完成流控和降级
使用Sentinel作为gateWay的限流、降级、系统保护工具。   
Sentinel 1.6.0 引入了 Sentinel API Gateway Adapter Common 模块，此模块中包含网关限流的规则和自定义 API 的实体和管理逻辑：

**GatewayFlowRule**：网关限流规则，针对 API Gateway 的场景定制的限流规则，可以针对不同 route 或自定义的 API 分组进行限流，支持针对请求中的参数、Header、来源 IP 等进行定制化的限流。

**ApiDefinition**：用户自定义的 API 定义分组，可以看做是一些 URL 匹配的组合。比如我们可以定义一个 API 叫 my_api，请求 path 模式为 /foo/** 和 /baz/** 的都归到 my_api 这个 API 分组下面。限流的时候可以针对这个自定义的 API 分组维度进行限流。

其中网关限流规则 GatewayFlowRule 的字段解释如下：
- resource：资源名称，可以是网关中的 route 名称或者用户自定义的 API 分组名称。
- resourceMode：规则是针对 API Gateway 的 route（RESOURCE_MODE_ROUTE_ID）还是用户在 Sentinel 中定义的 API 分组（RESOURCE_MODE_CUSTOM_API_NAME），默认是 route。
- grade：限流指标维度，同限流规则的 grade 字段
- count：限流阈值
- intervalSec：统计时间窗口，单位是秒，默认是 1 秒
- controlBehavior：流量整形的控制效果，同限流规则的 controlBehavior 字段，目前支持快速失败和匀速排队两种模式，默认是快速失败。
- burst：应对突发请求时额外允许的请求数目。
- maxQueueingTimeoutMs：匀速排队模式下的最长排队时间，单位是毫秒，仅在匀速排队模式下生效。
- paramItem：参数限流配置。若不提供，则代表不针对参数进行限流，该网关规则将会被转换成普通流控规则；否则会转换成热点规则。其中的字段：
- parseStrategy：从请求中提取参数的策略，目前支持提取来源 IP（PARAM_PARSE_STRATEGY_CLIENT_IP）、Host（PARAM_PARSE_STRATEGY_HOST）、任意 Header（PARAM_PARSE_STRATEGY_HEADER）和任意 URL 参数（PARAM_PARSE_STRATEGY_URL_PARAM）四种模式。
- fieldName：若提取策略选择 Header 模式或 URL 参数模式，则需要指定对应的 header 名称或 URL 参数名称。
- pattern 和 matchStrategy：为后续参数匹配特性预留，目前未实现。

用户可以通过 GatewayRuleManager.loadRules(rules) 手动加载网关规则，或通过 GatewayRuleManager.register2Property(property) 注册动态规则源动态推送（推荐方式）。

maven依赖
```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
<!-- sentinel适配支持Spring Cloud Gateway-->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-spring-cloud-gateway-adapter</artifactId>
</dependency>
```

gateway sentinel配置
```java
@Configuration
public class GatewayConfig {

	private final List<ViewResolver> viewResolvers;
	private final ServerCodecConfigurer serverCodecConfigurer;

	public GatewayConfig(ObjectProvider<List<ViewResolver>> viewResolversProvider, ServerCodecConfigurer serverCodecConfigurer) {
		this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
		this.serverCodecConfigurer = serverCodecConfigurer;
	}

	/**
	 * 配置限流的异常处理器:SentinelGatewayBlockExceptionHandler<br/>
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
```

指定参数限流
上面的配置是针对整个路由来限流的，如果我们只想对某个路由的参数做限流，那么可以使用参数限流方式：
```
 rules.add(new GatewayFlowRule("path_route")
     .setCount(1)
     .setIntervalSec(1)
     .setParamItem(new GatewayParamFlowItem()
                .setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM).setFieldName("vipType")
     )
 );
```
通过指定PARAM_PARSE_STRATEGY_URL_PARAM表示从url中获取参数，setFieldName指定参数名称

自定义API分组
```yaml
- id: product-center
  uri: lb://product-center
  predicates:
    - Path=/product/**
- id: order-center
  uri: lb://order-center
  predicates:
    - Path=/order/**
```
自定义分组代码：
```
private void initCustomizedApis() {
    Set<ApiDefinition> definitions = new HashSet<>();
    ApiDefinition api1 = new ApiDefinition("customized_api")
        .setPredicateItems(new HashSet<ApiPredicateItem>() {{
         // product完全匹配
         add(new ApiPathPredicateItem().setPattern("/product"));
         // order/开头的
         add(new ApiPathPredicateItem().setPattern("/order/**")
                .setMatchStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_PREFIX));
        }});
    definitions.add(api1);
    GatewayApiDefinitionManager.loadApiDefinitions(definitions);
}
```
然后需要给customized_api这个资源进行配置：
```
 rules.add(new GatewayFlowRule("customized_api")
      .setCount(1)
      .setIntervalSec(1)
 ); 
```

## 五、自定义异常提示
当触发限流后页面显示的是Blocked by Sentinel: FlowException，正常情况下，就算给出提示也要跟后端服务的数据格式一样，如果你后端都是JSON格式的数据，那么异常的提示也要是JSON的格式。

前面有配置SentinelGatewayBlockExceptionHandler，需要重写SentinelGatewayBlockExceptionHandler中的输出方法。
见：com.wordplay.gateway.handler.JsonSentinelGatewayBlockExceptionHandler

```
/**
 * 配置限流的异常处理器:JsonSentinelGatewayBlockExceptionHandler
 * 自定义异常提示：当发生限流、熔断异常时，会返回定义的提示信息。
 * @return
 */
@Bean
@Order(Ordered.HIGHEST_PRECEDENCE)
public JsonSentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
    // Register the block exception handler for Spring Cloud Gateway.
    return new JsonSentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
}
```

安装Sentinel  
Sentinel Dashboard 的地址:   
http://localhost:8080/#/dashboard/home
