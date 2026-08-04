package com.helix.profiler.async;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProfileModeTest {

    @Test
    void testProfileModeEnumValues() {
        assertEquals("cpu", ProfileMode.CPU.getValue());
        assertEquals("alloc", ProfileMode.ALLOC.getValue());
        assertEquals("lock", ProfileMode.LOCK.getValue());

        assertEquals(ProfileMode.CPU, ProfileMode.fromValue("cpu"));
        assertEquals(ProfileMode.ALLOC, ProfileMode.fromValue("ALLOC"));
        assertEquals(ProfileMode.LOCK, ProfileMode.fromValue("lock"));
        assertEquals(ProfileMode.CPU, ProfileMode.fromValue(null));
        assertEquals(ProfileMode.CPU, ProfileMode.fromValue("unknown"));
    }
}
