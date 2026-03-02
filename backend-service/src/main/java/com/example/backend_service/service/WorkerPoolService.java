package com.example.backend_service.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class WorkerPoolService {
    private ThreadPoolExecutor executor;

    private final int workerThreads = 8;
    private final int queueCapacity = 200;

    @PostConstruct
    public void init(){
        executor = new ThreadPoolExecutor(workerThreads, workerThreads, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity), new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    public void shutdown(){
        executor.shutdown();
    }

    public void submit(Runnable task){
        executor.execute(task);
    }

    public int getQueueSize(){
        return executor.getQueue().size();
    }

    public int getActiveCount(){
        return executor.getActiveCount();
    }
}
