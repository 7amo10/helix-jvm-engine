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
        String includePattern = System.getProperty("jmh.benchmark");
        if (includePattern == null || includePattern.isBlank()) {
            if (args != null && args.length > 0 && !args[0].isBlank()) {
                includePattern = args[0];
            } else {
                includePattern = "CompilationBenchmark|L4CacheBenchmark|DisruptorStreamingBenchmark";
            }
        }

        int warmup = Integer.parseInt(System.getProperty("jmh.warmup", "1"));
        int measurement = Integer.parseInt(System.getProperty("jmh.measurement", "2"));
        int forks = Integer.parseInt(System.getProperty("jmh.forks", "0"));

        Options opt = new OptionsBuilder()
                .include(includePattern)
                .forks(forks)
                .warmupIterations(warmup)
                .measurementIterations(measurement)
                .build();

        new Runner(opt).run();
    }
}
