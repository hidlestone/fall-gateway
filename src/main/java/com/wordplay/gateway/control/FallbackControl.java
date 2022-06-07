package com.wordplay.gateway.control;

import com.fallframework.platform.starter.api.response.ResponseResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhuangpf
 */
@RestController
public class FallbackControl {

	@GetMapping("/fallbacka")
	public ResponseResult fallbacka() {
		return ResponseResult.fail("服务降级");
	}

}
