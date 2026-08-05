package com.helix.cli;

import com.helix.api.CompiledRule;
import com.helix.cli.output.OutputFormatter;
import com.helix.cli.ui.TerminalRenderer;
import com.helix.core.RuleCompiler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "compile", description = "Compile a JSON rule into JVM bytecode", mixinStandardHelpOptions = true)
public class CompileCommand implements CliCommand {

    @Option(names = {"-r", "--rule"}, required = true, description = "Path to JSON rule file")
    private File ruleFile;

    @Option(names = {"-o", "--output"}, defaultValue = "text", description = "Output format: text, json, csv")
    private String outputFormat;

    @Option(names = {"-q", "--quiet"}, description = "Suppress non-essential output")
    private boolean quiet;

    @Override
    public Integer call() {
        try {
            if (!ruleFile.exists()) {
                TerminalRenderer.renderError("Rule file not found: " + ruleFile.getAbsolutePath());
                return 1;
            }

            String ruleJson = Files.readString(ruleFile.toPath());
            RuleCompiler compiler = new RuleCompiler();

            long start = System.nanoTime();
            CompiledRule rule = compiler.compile(ruleJson);
            long elapsedNanos = System.nanoTime() - start;

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("status", "SUCCESS");
            data.put("ruleName", rule.getName());
            data.put("ruleVersion", rule.getVersion());
            data.put("compilationTimeMs", String.format("%.3f", elapsedNanos / 1_000_000.0));

            OutputFormatter formatter = new OutputFormatter(outputFormat, quiet);
            String output = formatter.formatResult("Helix Rule Compilation", data);
            if (!output.isEmpty()) {
                System.out.println(output);
            }

            return 0;
        } catch (Exception e) {
            TerminalRenderer.renderError("Compilation failed: " + e.getMessage());
            return 1;
        }
    }
}
