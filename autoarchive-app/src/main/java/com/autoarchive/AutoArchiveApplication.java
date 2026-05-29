package com.autoarchive;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.autoarchive.audit.AuditProperties;
import com.autoarchive.config.ArchiveExecutionProperties;
import com.autoarchive.config.RetentionProperties;
import com.autoarchive.config.StorageProperties;

@SpringBootApplication
@EnableConfigurationProperties({
    StorageProperties.class,
    RetentionProperties.class,
    AuditProperties.class,
    ArchiveExecutionProperties.class
})
public class AutoArchiveApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(AutoArchiveApplication.class, args);
    }
}