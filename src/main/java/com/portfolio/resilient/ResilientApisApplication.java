package com.portfolio.resilient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ResilientApisApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResilientApisApplication.class, args);
    }
}
