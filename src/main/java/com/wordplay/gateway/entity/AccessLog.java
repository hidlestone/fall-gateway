package com.wordplay.gateway.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 网关访问日志
 *
 * @author zhuangpf
 */
@Getter
@Setter
public class AccessLog implements Serializable {

	private static final long serialVersionUID = -2338332402746743285L;

	/**
	 * 请求日志
	 */
	private RequestLog requestLog;

	/**
	 * 响应日志
	 */
	private ResponseLog responseLog;

	/**
	 * 请求时间
	 */
	private Date requestTime;

	/**
	 * 响应时间
	 */
	private Date responseTime;

	/**
	 * 执行时间
	 */
	private long executeTime;

	/**
	 * 请求日志
	 */
	@Getter
	@Setter
	public static class RequestLog {

		/**
		 * 请求头
		 */
		private String headers;

		/**
		 * 请求体
		 */
		private String requestBody;

		/**
		 * 请求方法
		 */
		private String method;

		/**
		 * 请求方地址
		 */
		private String address;

		/**
		 * 请求方地址
		 */
		private String url;

	}

	/**
	 * 响应日志
	 */
	@Getter
	@Setter
	public static class ResponseLog {

		/**
		 * 响应状态码
		 */
		private String statusCode;

		/**
		 * 响应头
		 */
		private String headers;

		/**
		 * 响应结果
		 */
		private String responseResult;

	}

}
