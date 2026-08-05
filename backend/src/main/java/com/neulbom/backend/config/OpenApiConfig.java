package com.neulbom.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI neulbomOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("늘봄(NEULBOM) API")
                        .description("성장형 캐릭터 기반 치매 조기 스크리닝 서비스 API")
                        .version("v1.1"));
    }
}
