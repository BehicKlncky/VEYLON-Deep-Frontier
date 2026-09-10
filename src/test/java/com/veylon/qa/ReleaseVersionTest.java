package com.veylon.qa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseVersionTest {

    @Test
    void gradleReleaseVersionIsArchitectureReleaseVersion() {
        assertEquals("0.5.10", System.getProperty("veylon.version"));
    }
}
