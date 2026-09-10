package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AudioLifecycleTest {
    @Test void efxDetectionCanRunAgainAfterContextTeardownButLogsOncePerLifetime() {
        var output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            var effects = new EfxProcessor();
            effects.init(false); effects.init(false); effects.close();
            effects.init(false); effects.init(false); effects.close();
            assertFalse(effects.enabled());
        } finally { System.setOut(original); }
        assertEquals(2, output.toString(StandardCharsets.UTF_8).lines().filter(s -> s.contains("EFX unavailable")).count());
    }
}
