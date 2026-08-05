package com.neulbom.backend.config;

import com.neulbom.backend.common.filter.RequestIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public RequestIdFilter requestIdFilter(RequestIdProperties properties) {
        return new RequestIdFilter(properties);
    }
}
