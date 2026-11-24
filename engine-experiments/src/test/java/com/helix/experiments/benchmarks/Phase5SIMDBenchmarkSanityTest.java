package com.helix.experiments.benchmarks;

import com.helix.core.batch.BatchDispatcher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Phase5SIMDBenchmarkSanityTest {

    @Test
    void testSimdBatchBenchmarkIsInstantiableAndParityHolds() throws Exception {
        SIMDBatchBenchmark benchmark = new SIMDBatchBenchmark();
        for (int size : new int[]{8, 64, 200, 1000}) {
            benchmark.batchSize = size;
            benchmark.setup();

            BatchDispatcher.DispatchResult simdResult = benchmark.benchmarkSimdBatch();
            BatchDispatcher.DispatchResult scalarResult = benchmark.benchmarkScalarBatch();
            boolean[] kernelResult = benchmark.benchmarkSimdVectorizedKernel();

            assertNotNull(simdResult);
            assertNotNull(scalarResult);
            assertNotNull(kernelResult);
            assertEquals(size, simdResult.getBatchSize());
            assertEquals(size, scalarResult.getBatchSize());
            assertEquals(size, kernelResult.length);
            assertArrayEquals(scalarResult.results(), simdResult.results(),
                    "SIMD and scalar benchmark paths must produce bit-exact results for batch size " + size);
            assertArrayEquals(scalarResult.results(), kernelResult,
                    "SIMD kernel and scalar paths must produce bit-exact results for batch size " + size);
            benchmark.tearDown();

            if (BatchDispatcher.isSimdCapable()) {
                assertEquals(BatchDispatcher.EvaluationMode.SIMD, simdResult.evaluationMode());
            }
            assertEquals(BatchDispatcher.EvaluationMode.SCALAR, scalarResult.evaluationMode());
        }
    }

    @Test
    void testVectorizabilityClassifierBenchmarkIsInstantiable() throws Exception {
        assertDoesNotThrow(() -> {
            VectorizabilityClassifierBenchmark benchmark = new VectorizabilityClassifierBenchmark();
            benchmark.setup();
            assertTrue(benchmark.benchmarkClassifyVectorizableAst());
            assertFalse(benchmark.benchmarkClassifyNonVectorizableAst());
            assertTrue(benchmark.benchmarkClassifyCompiledRuleCached());
            assertFalse(benchmark.benchmarkExtractReferencedFields().isEmpty());
        });
    }

    @Test
    void testColumnExtractorBenchmarkIsInstantiable() throws Exception {
        assertDoesNotThrow(() -> {
            ColumnExtractorBenchmark benchmark = new ColumnExtractorBenchmark();
            benchmark.setup();
            assertEquals(5, benchmark.benchmarkExtract200Contexts5Fields());
        });
    }
}
