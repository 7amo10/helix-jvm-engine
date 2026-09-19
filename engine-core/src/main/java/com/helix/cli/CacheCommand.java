package com.helix.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Picocli top-level command for distributed and tiered rule cache administration.
 * Subcommand 'l4' provides direct inspection and eviction tools for the Redis distributed cache.
 */
@Command(
        name = "cache",
        description = "Manage and inspect distributed and tiered rule caches",
        mixinStandardHelpOptions = true,
        subcommands = {
                L4CacheCommand.class
        }
)
public class CacheCommand implements CliCommand {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
}
