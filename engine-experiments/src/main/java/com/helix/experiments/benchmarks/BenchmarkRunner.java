package com.helix.experiments.benchmarks;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Runner harness for JMH benchmarks.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(CompilationBenchmark.class.getSimpleName())
                .forks(0)
                .warmupIterations(1)
                .measurementIterations(2)
                .build();

        new Runner(opt).run();
    }
}
