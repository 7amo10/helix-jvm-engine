package com.helix.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.Instrumentation;

/**
 * Entry points for the Helix Java Agent (premain for static attachment, agentmain for dynamic attachment).
 */
public class AgentMain {

    private static final Logger log = LoggerFactory.getLogger(AgentMain.class);
    private static volatile Instrumentation globalInstrumentation;
    private static volatile AgentConfiguration agentConfiguration;

    public static void premain(String agentArgs, Instrumentation inst) {
        initialize(agentArgs, inst, "premain");
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        initialize(agentArgs, inst, "agentmain");
    }

    private static synchronized void initialize(String agentArgs, Instrumentation inst, String entryPoint) {
        if (inst == null) {
            throw new AgentInitializationException("Instrumentation instance passed to " + entryPoint + " is null");
        }
        globalInstrumentation = inst;
        agentConfiguration = AgentConfiguration.parse(agentArgs);

        log.info("Helix Java Agent attached via {} (Retransformation: {}, Packages: {})",
                entryPoint, agentConfiguration.isEnableRetransformation(), agentConfiguration.getTargetPackages());
    }

    public static Instrumentation getInstrumentation() {
        return globalInstrumentation;
    }

    public static AgentConfiguration getConfiguration() {
        return agentConfiguration;
    }
}
