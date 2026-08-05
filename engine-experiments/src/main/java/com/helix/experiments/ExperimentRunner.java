package com.helix.experiments;

import com.helix.experiments.gc.GcStressExperiment;
import com.helix.experiments.jit.JitCompilationExperiment;
import com.helix.experiments.layout.ObjectLayoutExperiment;
import com.helix.experiments.metaspace.MetaspaceLeakExperiment;
import com.helix.experiments.safepoint.SafepointExperiment;

import java.util.Arrays;
import java.util.Locale;

/**
 * Unified CLI entrypoint to list and execute JVM performance experiments.
 */
public class ExperimentRunner {

    public static void main(String[] args) {
        if (args == null || args.length == 0 || isHelpOption(args[0])) {
            printHelp();
            return;
        }

        String command = args[0].toLowerCase(Locale.ROOT).trim();
        int exitCode = executeCommand(command);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int executeCommand(String command) {
        System.out.println("=================================================");
        System.out.println(" Helix JVM Performance Experiment Runner ");
        System.out.println("=================================================");

        switch (command) {
            case "metaspace":
            case "metaspace-leak":
                runMetaspaceExperiment();
                break;
            case "jit":
            case "jit-tiers":
                runJitExperiment();
                break;
            case "gc":
            case "gc-stress":
                runGcExperiment();
                break;
            case "layout":
            case "object-layout":
                runLayoutExperiment();
                break;
            case "safepoint":
                runSafepointExperiment();
                break;
            case "all":
                runAllExperiments();
                break;
            default:
                System.err.println("Unknown experiment command: '" + command + "'");
                printHelp();
                return 1;
        }
        return 0;
    }

    private static void runMetaspaceExperiment() {
        System.out.println("\n>>> Running Metaspace Leak Experiment...");
        MetaspaceLeakExperiment experiment = new MetaspaceLeakExperiment();
        System.out.println(experiment.runLeakScenario(10));
        System.out.println(experiment.runFixedScenario(10));
    }

    private static void runJitExperiment() {
        System.out.println("\n>>> Running JIT Compilation Tiers Experiment...");
        JitCompilationExperiment experiment = new JitCompilationExperiment();
        System.out.println(experiment.observeCompilationTiers("com.helix.RuleEngine::evaluate", 15000));
        System.out.println(experiment.testInliningLimits("com.helix.RuleEngine::small", 20, false));
    }

    private static void runGcExperiment() {
        System.out.println("\n>>> Running GC Stress & Reference Experiment...");
        GcStressExperiment experiment = new GcStressExperiment();
        System.out.println(experiment.weakReferenceImmediate(20));
        System.out.println(experiment.softReferenceUnderPressure(10, false));
    }

    private static void runLayoutExperiment() {
        System.out.println("\n>>> Running Object Layout Analysis Experiment...");
        ObjectLayoutExperiment experiment = new ObjectLayoutExperiment();
        System.out.println(experiment.analyzeLayout("Helix Rule Engine Sample String Target"));
    }

    private static void runSafepointExperiment() {
        System.out.println("\n>>> Running Safepoint Pause Experiment...");
        SafepointExperiment experiment = new SafepointExperiment();
        System.out.println(experiment.monitorSafepoints("G1CollectForAllocation", 5000));
    }

    private static void runAllExperiments() {
        runMetaspaceExperiment();
        runJitExperiment();
        runGcExperiment();
        runLayoutExperiment();
        runSafepointExperiment();
    }

    public static void printHelp() {
        System.out.println("""
            Usage: java -jar engine-experiments.jar <experiment-name>
            
            Available Experiments:
              metaspace-leak  (or 'metaspace')  - Demonstrates Metaspace memory leaks & cleanup.
              jit-tiers       (or 'jit')        - Observes HotSpot JIT tier compilation & inlining limits.
              gc-stress       (or 'gc')         - Evaluates Soft/Weak reference behavior during GC passes.
              object-layout   (or 'layout')     - Analyzes JVM object sizes, headers, and alignment using JOL.
              safepoint                         - Measures Time-To-Safepoint (TTSP) and pause latency.
              all                               - Executes all JVM experiment scenarios sequentially.
              help                              - Prints this help message.
            """);
    }

    private static boolean isHelpOption(String arg) {
        return Arrays.asList("help", "-h", "--help", "-help").contains(arg.toLowerCase(Locale.ROOT));
    }
}
