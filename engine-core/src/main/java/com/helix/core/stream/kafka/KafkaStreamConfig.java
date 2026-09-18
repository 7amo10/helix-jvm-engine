package com.helix.core.stream.kafka;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/**
 * Configuration for clustered Kafka rule streaming ingestion and result publishing.
 */
public class KafkaStreamConfig {

    public static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String DEFAULT_GROUP_ID = "helix-streaming-group";
    public static final String DEFAULT_AUTO_OFFSET_RESET = "earliest";
    public static final Duration DEFAULT_POLL_TIMEOUT = Duration.ofMillis(100);
    public static final int DEFAULT_MAX_POLL_RECORDS = 500;
    public static final int DEFAULT_MAX_IN_FLIGHT_PER_PARTITION = 1000;
    public static final int DEFAULT_LOW_WATERMARK_PER_PARTITION = 200;

    private final String bootstrapServers;
    private final String groupId;
    private final Set<String> inputTopics;
    private final String outputTopic;
    private final String autoOffsetReset;
    private final boolean enableAutoCommit;
    private final CommitMode commitMode;
    private final Duration pollTimeout;
    private final int maxPollRecords;
    private final int maxInFlightPerPartition;
    private final int lowWatermarkPerPartition;
    private final Map<String, Object> customConsumerProps;
    private final Map<String, Object> customProducerProps;

    public KafkaStreamConfig(String bootstrapServers, String groupId, Set<String> inputTopics,
                             String outputTopic, String autoOffsetReset, boolean enableAutoCommit,
                             CommitMode commitMode, Duration pollTimeout, int maxPollRecords,
                             int maxInFlightPerPartition, int lowWatermarkPerPartition,
                             Map<String, Object> customConsumerProps, Map<String, Object> customProducerProps) {
        this.bootstrapServers = (bootstrapServers != null && !bootstrapServers.isBlank()) ? bootstrapServers : DEFAULT_BOOTSTRAP_SERVERS;
        if (bootstrapServers != null && bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("bootstrapServers cannot be blank");
        }

        this.groupId = (groupId != null && !groupId.isBlank()) ? groupId : DEFAULT_GROUP_ID;
        if (groupId != null && groupId.isBlank()) {
            throw new IllegalArgumentException("groupId cannot be blank");
        }

        this.inputTopics = inputTopics != null ? Collections.unmodifiableSet(new HashSet<>(inputTopics)) : Collections.emptySet();
        this.outputTopic = outputTopic;
        this.autoOffsetReset = autoOffsetReset != null ? autoOffsetReset : DEFAULT_AUTO_OFFSET_RESET;
        this.enableAutoCommit = enableAutoCommit;
        this.commitMode = commitMode != null ? commitMode : CommitMode.SYNC;
        this.pollTimeout = pollTimeout != null ? pollTimeout : DEFAULT_POLL_TIMEOUT;
        this.maxPollRecords = maxPollRecords > 0 ? maxPollRecords : DEFAULT_MAX_POLL_RECORDS;
        this.maxInFlightPerPartition = maxInFlightPerPartition > 0 ? maxInFlightPerPartition : DEFAULT_MAX_IN_FLIGHT_PER_PARTITION;
        this.lowWatermarkPerPartition = lowWatermarkPerPartition > 0 ? lowWatermarkPerPartition : DEFAULT_LOW_WATERMARK_PER_PARTITION;

        if (this.lowWatermarkPerPartition > this.maxInFlightPerPartition) {
            throw new IllegalArgumentException("lowWatermarkPerPartition (" + this.lowWatermarkPerPartition
                    + ") cannot exceed maxInFlightPerPartition (" + this.maxInFlightPerPartition + ")");
        }

        this.customConsumerProps = customConsumerProps != null ? Collections.unmodifiableMap(new HashMap<>(customConsumerProps)) : Collections.emptyMap();
        this.customProducerProps = customProducerProps != null ? Collections.unmodifiableMap(new HashMap<>(customProducerProps)) : Collections.emptyMap();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static KafkaStreamConfig defaultConfig() {
        return new Builder().build();
    }

    public Properties toConsumerProperties() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, String.valueOf(enableAutoCommit));
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, String.valueOf(maxPollRecords));
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
        props.putAll(customConsumerProps);
        return props;
    }

    public Properties toProducerProperties() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.putAll(customProducerProps);
        return props;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getGroupId() {
        return groupId;
    }

    public Set<String> getInputTopics() {
        return inputTopics;
    }

    public String getOutputTopic() {
        return outputTopic;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public boolean isEnableAutoCommit() {
        return enableAutoCommit;
    }

    public CommitMode getCommitMode() {
        return commitMode;
    }

    public Duration getPollTimeout() {
        return pollTimeout;
    }

    public int getMaxPollRecords() {
        return maxPollRecords;
    }

    public int getMaxInFlightPerPartition() {
        return maxInFlightPerPartition;
    }

    public int getLowWatermarkPerPartition() {
        return lowWatermarkPerPartition;
    }

    public Map<String, Object> getCustomConsumerProps() {
        return customConsumerProps;
    }

    public Map<String, Object> getCustomProducerProps() {
        return customProducerProps;
    }

    public static class Builder {
        private String bootstrapServers = DEFAULT_BOOTSTRAP_SERVERS;
        private String groupId = DEFAULT_GROUP_ID;
        private Set<String> inputTopics = new HashSet<>();
        private String outputTopic;
        private String autoOffsetReset = DEFAULT_AUTO_OFFSET_RESET;
        private boolean enableAutoCommit = false;
        private CommitMode commitMode = CommitMode.SYNC;
        private Duration pollTimeout = DEFAULT_POLL_TIMEOUT;
        private int maxPollRecords = DEFAULT_MAX_POLL_RECORDS;
        private int maxInFlightPerPartition = DEFAULT_MAX_IN_FLIGHT_PER_PARTITION;
        private int lowWatermarkPerPartition = DEFAULT_LOW_WATERMARK_PER_PARTITION;
        private Map<String, Object> customConsumerProps = new HashMap<>();
        private Map<String, Object> customProducerProps = new HashMap<>();

        public Builder bootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
            return this;
        }

        public Builder groupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        public Builder inputTopic(String topic) {
            if (topic != null) {
                this.inputTopics.add(topic);
            }
            return this;
        }

        public Builder inputTopics(Set<String> topics) {
            if (topics != null) {
                this.inputTopics.addAll(topics);
            }
            return this;
        }

        public Builder outputTopic(String outputTopic) {
            this.outputTopic = outputTopic;
            return this;
        }

        public Builder autoOffsetReset(String autoOffsetReset) {
            this.autoOffsetReset = autoOffsetReset;
            return this;
        }

        public Builder enableAutoCommit(boolean enableAutoCommit) {
            this.enableAutoCommit = enableAutoCommit;
            return this;
        }

        public Builder commitMode(CommitMode commitMode) {
            this.commitMode = commitMode;
            return this;
        }

        public Builder pollTimeout(Duration pollTimeout) {
            this.pollTimeout = pollTimeout;
            return this;
        }

        public Builder maxPollRecords(int maxPollRecords) {
            this.maxPollRecords = maxPollRecords;
            return this;
        }

        public Builder maxInFlightPerPartition(int maxInFlightPerPartition) {
            this.maxInFlightPerPartition = maxInFlightPerPartition;
            return this;
        }

        public Builder lowWatermarkPerPartition(int lowWatermarkPerPartition) {
            this.lowWatermarkPerPartition = lowWatermarkPerPartition;
            return this;
        }

        public Builder consumerProperty(String key, Object value) {
            this.customConsumerProps.put(key, value);
            return this;
        }

        public Builder producerProperty(String key, Object value) {
            this.customProducerProps.put(key, value);
            return this;
        }

        public KafkaStreamConfig build() {
            return new KafkaStreamConfig(
                    bootstrapServers, groupId, inputTopics, outputTopic,
                    autoOffsetReset, enableAutoCommit, commitMode, pollTimeout,
                    maxPollRecords, maxInFlightPerPartition, lowWatermarkPerPartition,
                    customConsumerProps, customProducerProps
            );
        }
    }
}
