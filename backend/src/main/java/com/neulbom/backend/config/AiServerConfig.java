package com.neulbom.backend.config;

import java.net.http.HttpClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

@Configuration
public class AiServerConfig {

    @Bean
    @Qualifier("aiServerRestClient")
    public RestClient aiServerRestClient(
            AiServerProperties properties,
            ObjectMapper objectMapper
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();

        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(
                            MappingJackson2HttpMessageConverter.class::isInstance
                    );
                    converters.add(
                            new MappingJackson2HttpMessageConverter(objectMapper)
                    );
                })
                .build();
    }
}