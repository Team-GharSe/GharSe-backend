package com.gharse.health;

import com.gharse.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<?> health() {
        return ApiResponse.ok("Service is UP");
    }
}