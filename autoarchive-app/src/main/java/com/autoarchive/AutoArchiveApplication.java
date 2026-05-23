package com.autoarchive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.autoarchive.config.StorageProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class AutoArchiveApplication {

    public static void main(String[] args) {
        SpringApplication.run(AutoArchiveApplication.class, args);
    }
}
