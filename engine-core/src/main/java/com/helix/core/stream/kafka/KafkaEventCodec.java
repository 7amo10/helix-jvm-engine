package com.helix.core.stream.kafka;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.api.ExecutionContext;
import com.helix.api.stream.RuleEvent;
import com.helix.api.stream.StreamResult;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Serializer and deserializer for Kafka stream records, rule events, and execution results.
 */
public class KafkaEventCodec {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventCodec.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static byte[] serializeResult(StreamResult result) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("eventId", result.getEventId());
            map.put("topic", result.getTopic());
            map.put("ruleName", result.getRuleName());
            map.put("success", result.isSuccess());
            map.put("result", result.getResultRaw());
            map.put("executionTimeNanos", result.getExecutionTimeNanos());
            if (result.getError().isPresent()) {
                map.put("error", result.getError().get().getMessage());
            }
            return MAPPER.writeValueAsBytes(map);
        } catch (Exception e) {
            log.error("Failed to serialize StreamResult for event {}", result.getEventId(), e);
            return ("{\"error\":\"Serialization failure: " + e.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    public static StreamResult deserializeResult(byte[] data) {
        try {
            JsonNode node = MAPPER.readTree(data);
            String eventId = node.has("eventId") ? node.get("eventId").asText() : null;
            String topic = node.has("topic") ? node.get("topic").asText() : null;
            String ruleName = node.has("ruleName") ? node.get("ruleName").asText() : null;
            boolean success = node.has("success") && node.get("success").asBoolean();
            long executionTimeNanos = node.has("executionTimeNanos") ? node.get("executionTimeNanos").asLong() : 0L;

            Object result = null;
            if (node.has("result")) {
                JsonNode resNode = node.get("result");
                if (resNode.isBoolean()) {
                    result = resNode.asBoolean();
                } else if (resNode.isNumber()) {
                    result = resNode.numberValue();
                } else if (resNode.isTextual()) {
                    result = resNode.asText();
                } else {
                    result = MAPPER.convertValue(resNode, Map.class);
                }
            }

            Throwable error = null;
            if (node.has("error") && !node.get("error").isNull()) {
                error = new RuntimeException(node.get("error").asText());
            }

            return new StreamResult(eventId, topic, ruleName, success, result, error, executionTimeNanos);
        } catch (Exception e) {
            log.warn("Failed to deserialize StreamResult from JSON payload", e);
            return StreamResult.failure(null, null, null, e, 0L);
        }
    }

    public static byte[] serializeEvent(RuleEvent event) {
        try {
            Map<String, Object> map = new HashMap<>();
            map.put("eventId", event.getEventId());
            map.put("topic", event.getTopic());
            map.put("ruleName", event.getRuleName());
            map.put("timestamp", event.getTimestamp());
            map.put("headers", event.getHeaders());
            map.put("variables", event.getContext() != null ? extractVariables(event.getContext()) : Map.of());
            return MAPPER.writeValueAsBytes(map);
        } catch (Exception e) {
            log.error("Failed to serialize RuleEvent {}", event.getEventId(), e);
            return ("{\"error\":\"Serialization failure\"}").getBytes(StandardCharsets.UTF_8);
        }
    }

    public static RuleEvent deserializeEvent(String topic, String key, byte[] payload, Headers headers) {
        String eventId = null;
        String ruleName = null;
        ExecutionContext context = new ExecutionContext();
        Map<String, String> headerMap = new HashMap<>();

        if (headers != null) {
            for (Header header : headers) {
                String hKey = header.key();
                String hVal = header.value() != null ? new String(header.value(), StandardCharsets.UTF_8) : "";
                headerMap.put(hKey, hVal);
                if ("ruleName".equalsIgnoreCase(hKey) || "rule".equalsIgnoreCase(hKey)) {
                    ruleName = hVal;
                } else if ("eventId".equalsIgnoreCase(hKey) || "id".equalsIgnoreCase(hKey)) {
                    eventId = hVal;
                }
            }
        }

        if (payload != null && payload.length > 0) {
            try {
                JsonNode root = MAPPER.readTree(payload);
                if (root.isObject()) {
                    if (eventId == null && root.has("eventId")) {
                        eventId = root.get("eventId").asText();
                    }
                    if (ruleName == null && root.has("ruleName")) {
                        ruleName = root.get("ruleName").asText();
                    }

                    if (root.has("variables") && root.get("variables").isObject()) {
                        populateContext(context, root.get("variables"));
                    } else {
                        populateContext(context, root);
                    }
                } else if (root.isValueNode()) {
                    context.setVariable("payload", root.asText());
                }
            } catch (Exception e) {
                String strPayload = new String(payload, StandardCharsets.UTF_8);
                context.setVariable("rawPayload", strPayload);
            }
        }

        if (key != null) {
            context.setVariable("messageKey", key);
        }

        return new RuleEvent(eventId, topic, ruleName, context, System.currentTimeMillis(), headerMap);
    }

    private static void populateContext(ExecutionContext context, JsonNode objectNode) {
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String k = field.getKey();
            if ("ruleName".equals(k) || "eventId".equals(k) || "timestamp".equals(k) || "headers".equals(k)) {
                continue;
            }
            JsonNode v = field.getValue();
            if (v.isBoolean()) {
                context.setVariable(k, v.asBoolean());
            } else if (v.isIntegralNumber()) {
                context.setVariable(k, v.asLong());
            } else if (v.isFloatingPointNumber()) {
                context.setVariable(k, v.asDouble());
            } else if (v.isTextual()) {
                context.setVariable(k, v.asText());
            } else if (v.isNull()) {
                context.setVariable(k, null);
            } else {
                context.setVariable(k, MAPPER.convertValue(v, Object.class));
            }
        }
    }

    private static Map<String, Object> extractVariables(ExecutionContext context) {
        Map<String, Object> map = new HashMap<>();
        try {
            java.lang.reflect.Field field = ExecutionContext.class.getDeclaredField("variables");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Object> vars = (Map<String, Object>) field.get(context);
            if (vars != null) {
                map.putAll(vars);
            }
        } catch (Exception ignored) {
        }
        return map;
    }
}
