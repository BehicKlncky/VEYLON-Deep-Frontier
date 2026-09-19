package com.veylon.qa;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReleaseVersionTest {

    @Test
    void gradleReleaseVersionIsArchitectureReleaseVersion() {
        assertEquals("0.8.0", System.getProperty("veylon.version"));
    }
}
