package com.autoarchive.storage;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Component;

@Component
public class VirtualThreadArchiveExecutor {

    public ExecutorService newExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
