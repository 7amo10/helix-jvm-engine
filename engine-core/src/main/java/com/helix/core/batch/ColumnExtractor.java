package com.helix.core.batch;

import com.helix.api.ExecutionContext;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * High-performance off-heap columnar data extractor utilizing Panama Foreign Function and Memory (FFM) API.
 * Transposes row-major {@link ExecutionContext}[] batches into contiguous off-heap {@link MemorySegment}
 * double-precision vectors for hardware SIMD vectorization.
 */
public final class ColumnExtractor {

    private ColumnExtractor() {
        // Private constructor for utility class
    }

    /**
     * Extracts named numeric fields from a row-major batch of {@link ExecutionContext} instances
     * into off-heap columnar {@link MemorySegment} allocations managed by the provided {@link Arena}.
     *
     * <p>Missing fields, non-numeric values, or null contexts are safely encoded as {@link Double#NaN}.
     * The allocated memory is bounded by the lifetime of the enclosing {@link Arena}.</p>
     *
     * @param batch  array of execution contexts (rows)
     * @param fields list of variable names to extract as columns
     * @param arena  off-heap memory arena (e.g., {@link Arena#ofConfined()})
     * @return unmodifiable map of field name to off-heap columnar {@link MemorySegment}
     * @throws NullPointerException if any argument is null
     */
    public static Map<String, MemorySegment> extract(
            ExecutionContext[] batch,
            List<String> fields,
            Arena arena) {
        Objects.requireNonNull(batch, "batch cannot be null");
        Objects.requireNonNull(fields, "fields cannot be null");
        Objects.requireNonNull(arena, "arena cannot be null");

        if (fields.isEmpty()) {
            return Collections.emptyMap();
        }

        int rowCount = batch.length;
        long byteSize = (long) rowCount * ValueLayout.JAVA_DOUBLE.byteSize();
        long byteAlignment = ValueLayout.JAVA_DOUBLE.byteAlignment();

        Map<String, MemorySegment> columns = new LinkedHashMap<>(fields.size());

        for (String field : fields) {
            Objects.requireNonNull(field, "field name cannot be null");
            MemorySegment segment = arena.allocate(byteSize, byteAlignment);

            for (int i = 0; i < rowCount; i++) {
                ExecutionContext ctx = batch[i];
                double val = (ctx != null) ? ctx.getDouble(field) : Double.NaN;
                segment.setAtIndex(ValueLayout.JAVA_DOUBLE, i, val);
            }

            columns.put(field, segment);
        }

        return Collections.unmodifiableMap(columns);
    }
}
