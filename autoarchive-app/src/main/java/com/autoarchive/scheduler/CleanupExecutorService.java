package com.autoarchive.scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

@Component
public class CleanupExecutorService {

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ExecutorService executor() {
        return executor;
    }
}

