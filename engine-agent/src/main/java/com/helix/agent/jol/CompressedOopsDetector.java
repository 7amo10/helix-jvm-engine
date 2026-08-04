package com.helix.agent.jol;

import org.openjdk.jol.vm.VM;

public class CompressedOopsDetector {

    public static boolean isCompressedOopsEnabled() {
        try {
            return VM.current().details().toLowerCase().contains("compressed oops");
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getVMDetails() {
        try {
            return VM.current().details();
        } catch (Throwable t) {
            return "VM details unavailable: " + t.getMessage();
        }
    }
}
