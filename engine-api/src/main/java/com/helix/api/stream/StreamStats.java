package com.helix.api.stream;

/**
 * Immutable snapshot of streaming runtime metrics and ring buffer capacity.
 */
public class StreamStats {

    private final long totalPublished;
    private final long totalProcessed;
    private final long totalFailed;
    private final long totalDropped;
    private final int ringBufferSize;
    private final long remainingCapacity;

    public StreamStats(long totalPublished, long totalProcessed, long totalFailed, long totalDropped, int ringBufferSize, long remainingCapacity) {
        this.totalPublished = totalPublished;
        this.totalProcessed = totalProcessed;
        this.totalFailed = totalFailed;
        this.totalDropped = totalDropped;
        this.ringBufferSize = ringBufferSize;
        this.remainingCapacity = remainingCapacity;
    }

    public long getTotalPublished() {
        return totalPublished;
    }

    public long getTotalProcessed() {
        return totalProcessed;
    }

    public long getTotalFailed() {
        return totalFailed;
    }

    public long getTotalDropped() {
        return totalDropped;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }

    public long getRemainingCapacity() {
        return remainingCapacity;
    }

    @Override
    public String toString() {
        return "StreamStats{" +
                "totalPublished=" + totalPublished +
                ", totalProcessed=" + totalProcessed +
                ", totalFailed=" + totalFailed +
                ", totalDropped=" + totalDropped +
                ", ringBufferSize=" + ringBufferSize +
                ", remainingCapacity=" + remainingCapacity +
                '}';
    }
}
