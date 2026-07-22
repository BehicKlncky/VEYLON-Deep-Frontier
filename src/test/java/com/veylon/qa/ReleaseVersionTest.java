package com.veylon.qa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseVersionTest {

    @Test
    void gradleReleaseVersionIsStabilityPatchVersion() {
        assertEquals("0.3.1", System.getProperty("veylon.version"));
    }
}
