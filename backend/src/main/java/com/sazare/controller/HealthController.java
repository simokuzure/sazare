package com.sazare.controller;

import com.sazare.common.ApiResponse;
import com.sazare.vo.HealthVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
public class HealthController {

    @GetMapping("/health")
    public ApiResponse<HealthVO> health() {
        return ApiResponse.success(new HealthVO("UP", "sazare", OffsetDateTime.now()));
    }
}
