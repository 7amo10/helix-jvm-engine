package com.helix.core.executor;

public class ExecutorConfiguration {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;
    private final long keepAliveTimeSeconds;
    private final String threadNamePrefix;

    public ExecutorConfiguration() {
        this(Runtime.getRuntime().availableProcessors(),
             Runtime.getRuntime().availableProcessors() * 2,
             1000,
             60L,
             "helix-executor-pool-");
    }

    public ExecutorConfiguration(int corePoolSize, int maxPoolSize, int queueCapacity, long keepAliveTimeSeconds, String threadNamePrefix) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
        this.keepAliveTimeSeconds = keepAliveTimeSeconds;
        this.threadNamePrefix = threadNamePrefix;
    }

    public int getCorePoolSize() {
        return corePoolSize;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public long getKeepAliveTimeSeconds() {
        return keepAliveTimeSeconds;
    }

    public String getThreadNamePrefix() {
        return threadNamePrefix;
    }
}
