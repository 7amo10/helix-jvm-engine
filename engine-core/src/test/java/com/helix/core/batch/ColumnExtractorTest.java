package com.helix.core.batch;

import com.helix.api.ExecutionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ColumnExtractor: Panama FFM Off-Heap Row-to-Columnar Transposition")
class ColumnExtractorTest {

    @Test
    @DisplayName("Transposes row-major ExecutionContext batch to columnar MemorySegments accurately")
    void testRowToColumnarTranspositionAccuracy() {
        ExecutionContext[] batch = new ExecutionContext[] {
                new ExecutionContext(Map.of("amount", 100.5, "risk", 0.12, "age", 25.0)),
                new ExecutionContext(Map.of("amount", 250.0, "risk", 0.45, "age", 40.0)),
                new ExecutionContext(Map.of("amount", 999.99, "risk", 0.88, "age", 62.0))
        };

        List<String> fields = List.of("amount", "risk", "age");

        try (Arena arena = Arena.ofConfined()) {
            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, fields, arena);

            assertEquals(3, columns.size());
            assertTrue(columns.containsKey("amount"));
            assertTrue(columns.containsKey("risk"));
            assertTrue(columns.containsKey("age"));

            MemorySegment amountSeg = columns.get("amount");
            MemorySegment riskSeg = columns.get("risk");
            MemorySegment ageSeg = columns.get("age");

            assertEquals(3 * Double.BYTES, amountSeg.byteSize());
            assertEquals(3 * Double.BYTES, riskSeg.byteSize());
            assertEquals(3 * Double.BYTES, ageSeg.byteSize());

            assertEquals(100.5, amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), 0.0001);
            assertEquals(250.0, amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 1), 0.0001);
            assertEquals(999.99, amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2), 0.0001);

            assertEquals(0.12, riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), 0.0001);
            assertEquals(0.45, riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 1), 0.0001);
            assertEquals(0.88, riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2), 0.0001);

            assertEquals(25.0, ageSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), 0.0001);
            assertEquals(40.0, ageSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 1), 0.0001);
            assertEquals(62.0, ageSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2), 0.0001);
        }
    }

    @Test
    @DisplayName("Encodes missing fields and null contexts safely as Double.NaN")
    void testMissingFieldsAndNullContextsEncodedAsNaN() {
        ExecutionContext[] batch = new ExecutionContext[] {
                new ExecutionContext(Map.of("amount", 100.0)),
                new ExecutionContext(Map.of("risk", 0.5)),
                null,
                new ExecutionContext(Map.of("amount", "not-a-number", "risk", 0.9))
        };

        List<String> fields = List.of("amount", "risk");

        try (Arena arena = Arena.ofConfined()) {
            Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, fields, arena);

            MemorySegment amountSeg = columns.get("amount");
            MemorySegment riskSeg = columns.get("risk");

            assertEquals(100.0, amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), 0.0001);
            assertTrue(Double.isNaN(amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 1)));
            assertTrue(Double.isNaN(amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2)));
            assertTrue(Double.isNaN(amountSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 3)));

            assertTrue(Double.isNaN(riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0)));
            assertEquals(0.5, riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 1), 0.0001);
            assertTrue(Double.isNaN(riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2)));
            assertEquals(0.9, riskSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, 3), 0.0001);
        }
    }

    @Test
    @DisplayName("Handles non-power-of-two and boundary batch sizes correctly")
    void testArbitraryBatchSizes() {
        int[] batchSizes = {1, 7, 8, 9, 15, 16, 200, 1001};

        List<String> fields = List.of("x", "y");

        try (Arena arena = Arena.ofConfined()) {
            for (int size : batchSizes) {
                ExecutionContext[] batch = new ExecutionContext[size];
                for (int i = 0; i < size; i++) {
                    batch[i] = new ExecutionContext(Map.of("x", (double) i, "y", (double) (i * 2)));
                }

                Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, fields, arena);
                MemorySegment xSeg = columns.get("x");
                MemorySegment ySeg = columns.get("y");

                assertEquals((long) size * Double.BYTES, xSeg.byteSize());
                assertEquals((long) size * Double.BYTES, ySeg.byteSize());

                for (int i = 0; i < size; i++) {
                    assertEquals((double) i, xSeg.getAtIndex(ValueLayout.JAVA_DOUBLE, i), 0.00001);
                    assertEquals((double) (i * 2), ySeg.getAtIndex(ValueLayout.JAVA_DOUBLE, i), 0.00001);
                }
            }
        }
    }

    @Test
    @DisplayName("Frees native memory segments upon Arena close without leaks across 10,000 iterations")
    void testOffHeapMemoryFreedOnArenaCloseWithoutLeaks() {
        List<String> fields = List.of("f1", "f2", "f3", "f4");
        ExecutionContext[] batch = new ExecutionContext[128];
        for (int i = 0; i < 128; i++) {
            batch[i] = new ExecutionContext(Map.of(
                    "f1", (double) i,
                    "f2", (double) (i + 1),
                    "f3", (double) (i + 2),
                    "f4", (double) (i + 3)
            ));
        }

        MemorySegment lastSegment = null;

        // Perform 10,000 allocations and closures
        for (int iter = 0; iter < 10_000; iter++) {
            try (Arena arena = Arena.ofConfined()) {
                Map<String, MemorySegment> columns = ColumnExtractor.extract(batch, fields, arena);
                lastSegment = columns.get("f1");
                assertEquals(0.0, lastSegment.getAtIndex(ValueLayout.JAVA_DOUBLE, 0), 0.001);
            }

            // Assert that closed arena invalidates the segment
            assertNotNull(lastSegment);
            assertFalse(lastSegment.scope().isAlive(), "Segment scope must not be alive after Arena closes");

            MemorySegment finalLastSegment = lastSegment;
            assertThrows(IllegalStateException.class, () ->
                    finalLastSegment.getAtIndex(ValueLayout.JAVA_DOUBLE, 0)
            );
        }
    }

    @Test
    @DisplayName("Handles empty batch, empty fields, and null parameters safely")
    void testEmptyAndEdgeCases() {
        try (Arena arena = Arena.ofConfined()) {
            // Empty batch
            Map<String, MemorySegment> emptyBatchRes = ColumnExtractor.extract(
                    new ExecutionContext[0], List.of("amount"), arena
            );
            assertTrue(emptyBatchRes.containsKey("amount"));
            assertEquals(0, emptyBatchRes.get("amount").byteSize());

            // Empty fields
            Map<String, MemorySegment> emptyFieldsRes = ColumnExtractor.extract(
                    new ExecutionContext[] { new ExecutionContext() }, List.of(), arena
            );
            assertTrue(emptyFieldsRes.isEmpty());

            // Null checks
            assertThrows(NullPointerException.class, () ->
                    ColumnExtractor.extract(null, List.of("x"), arena)
            );
            assertThrows(NullPointerException.class, () ->
                    ColumnExtractor.extract(new ExecutionContext[0], null, arena)
            );
            assertThrows(NullPointerException.class, () ->
                    ColumnExtractor.extract(new ExecutionContext[0], List.of("x"), null)
            );
        }
    }
}
