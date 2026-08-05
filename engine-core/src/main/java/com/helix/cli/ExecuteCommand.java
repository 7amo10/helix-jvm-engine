package com.helix.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.api.CompiledRule;
import com.helix.api.ExecutionContext;
import com.helix.api.ExecutionResult;
import com.helix.cli.output.OutputFormatter;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.RuleCompiler;
import com.helix.core.executor.AsyncExecutor;
import com.helix.core.executor.BatchExecutor;
import com.helix.core.executor.SyncExecutor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

@Command(name = "execute", description = "Compile and execute a rule against input context", mixinStandardHelpOptions = true)
public class ExecuteCommand implements CliCommand {

    @Option(names = {"-r", "--rule"}, required = true, description = "Path to JSON rule file")
    private File ruleFile;

    @Option(names = {"-c", "--context"}, required = true, description = "Path to JSON context variables file")
    private File contextFile;

    @Option(names = {"-m", "--mode"}, defaultValue = "sync", description = "Execution mode: sync, async, batch")
    private String mode;

    @Option(names = {"-o", "--output"}, defaultValue = "text", description = "Output format: text, json, csv")
    private String outputFormat;

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-essential output")
    private boolean quiet;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Integer call() {
        try {
            if (!ruleFile.exists() || !contextFile.exists()) {
                TerminalRenderer.renderError("Rule file or context file does not exist.");
                return 1;
            }

            String ruleJson = Files.readString(ruleFile.toPath());
            String contextJson = Files.readString(contextFile.toPath());

            RuleCompiler compiler = new RuleCompiler();
            CompiledRule rule = compiler.compile(ruleJson);

            Map<String, Object> vars = mapper.readValue(contextJson, new TypeReference<Map<String, Object>>() {});
            ExecutionContext context = new ExecutionContext(vars);

            ExecutionResult result;
            String execMode = mode.toLowerCase();

            switch (execMode) {
                case "async" -> {
                    try (AsyncExecutor asyncExec = new AsyncExecutor()) {
                        CompletableFuture<ExecutionResult> future = asyncExec.executeAsync(rule, context);
                        result = future.get();
                    }
                }
                case "batch" -> {
                    BatchExecutor batchExec = new BatchExecutor(4);
                    List<ExecutionResult> results = batchExec.executeBatch(rule, List.of(context));
                    result = results.get(0);
                }
                default -> {
                    SyncExecutor syncExec = new SyncExecutor();
                    result = syncExec.execute(rule, context);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", result.isSuccess() ? "SUCCESS" : "FAILURE");
            data.put("ruleName", rule.getName());
            data.put("executionMode", execMode.toUpperCase());
            data.put("resultValue", result.getResult().orElse("null"));
            data.put("durationMs", String.format("%.3f", result.getExecutionTimeNanos() / 1_000_000.0));
            if (!result.isSuccess()) {
                data.put("error", result.getError().map(Throwable::getMessage).orElse("Unknown Error"));
            }

            OutputFormatter formatter = new OutputFormatter(outputFormat, quiet);
            String output = formatter.formatResult("Helix Execution Outcome", data);
            if (!output.isEmpty()) {
                System.out.println(output);
            }

            return result.isSuccess() ? 0 : 2;

        } catch (Exception e) {
            TerminalRenderer.renderError("Execution failed: " + e.getMessage());
            return 2;
        }
    }
}
