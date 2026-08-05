package com.helix.cli.ui;

/**
 * ANSI colored console helper for rendering CLI messages.
 */
public class TerminalRenderer {

    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_BOLD = "\u001B[1m";

    public static void renderHeader(String title) {
        System.out.println(ANSI_BOLD + ANSI_CYAN + "=== " + title + " ===" + ANSI_RESET);
    }

    public static void renderSuccess(String message) {
        System.out.println(ANSI_GREEN + "[SUCCESS] " + message + ANSI_RESET);
    }

    public static void renderInfo(String message) {
        System.out.println(ANSI_CYAN + "[INFO] " + message + ANSI_RESET);
    }

    public static void renderWarning(String message) {
        System.out.println(ANSI_YELLOW + "[WARN] " + message + ANSI_RESET);
    }

    public static void renderError(String message) {
        System.err.println(ANSI_RED + "[ERROR] " + message + ANSI_RESET);
    }
}
