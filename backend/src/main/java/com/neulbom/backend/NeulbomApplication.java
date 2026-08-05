package com.neulbom.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NeulbomApplication {

    public static void main(String[] args) {
        SpringApplication.run(NeulbomApplication.class, args);
    }
}
