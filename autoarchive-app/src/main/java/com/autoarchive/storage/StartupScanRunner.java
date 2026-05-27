package com.autoarchive.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.autoarchive.config.StorageProperties;
import com.autoarchive.scheduler.CleanupRunService;

@Component
public class StartupScanRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupScanRunner.class);

    private final StorageProperties storageProperties;
    private final CleanupRunService cleanupRunService;
    
    public StartupScanRunner(
            StorageProperties storageProperties,
            CleanupRunService cleanupRunService) {
        this.storageProperties = storageProperties;
        this.cleanupRunService = cleanupRunService;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Startup scan trigger (archive root: {})", storageProperties.archiveRoot());
        cleanupRunService.runOnce("startup");
    }
}