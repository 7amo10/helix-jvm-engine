package com.helix.cli.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsciiTableRendererTest {

    @Test
    @DisplayName("Should render key-value ASCII table with headers and borders")
    void testRenderKeyValueTable() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("Host", "localhost:6379");
        data.put("Hits", 100);
        data.put("Misses", 5);
        data.put("Hit Ratio", "95.24%");

        String table = AsciiTableRenderer.renderKeyValueTable("TEST STATS", data);

        assertNotNull(table);
        assertTrue(table.contains("TEST STATS"));
        assertTrue(table.contains("Metric"));
        assertTrue(table.contains("Value"));
        assertTrue(table.contains("localhost:6379"));
        assertTrue(table.contains("95.24%"));
        assertTrue(table.startsWith("+"));
        assertTrue(table.endsWith("+\n"));
    }

    @Test
    @DisplayName("Should render generic tabular data with multiple columns")
    void testRenderGenericTable() {
        List<String> headers = List.of("ID", "Rule Name", "Status");
        List<List<String>> rows = List.of(
                List.of("1", "DiscountRule", "ACTIVE"),
                List.of("2", "FraudCheck", "DISABLED")
        );

        String table = AsciiTableRenderer.renderTable("RULES OVERVIEW", headers, rows);

        assertNotNull(table);
        assertTrue(table.contains("DiscountRule"));
        assertTrue(table.contains("FraudCheck"));
        assertTrue(table.contains("ACTIVE"));
        assertTrue(table.contains("DISABLED"));
    }

    @Test
    @DisplayName("Should handle empty data gracefully")
    void testEmptyData() {
        String result = AsciiTableRenderer.renderKeyValueTable("EMPTY", Map.of());
        assertTrue(result.isEmpty());
    }
}
