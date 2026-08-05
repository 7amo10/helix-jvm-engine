package com.helix.cli;

import java.util.concurrent.Callable;

/**
 * Base interface for all Helix CLI commands.
 */
public interface CliCommand extends Callable<Integer> {
    /**
     * Executes the command logic.
     *
     * @return exit status code (0 for success, 1 for compilation error, 2 for execution error)
     */
    @Override
    Integer call() throws Exception;
}
