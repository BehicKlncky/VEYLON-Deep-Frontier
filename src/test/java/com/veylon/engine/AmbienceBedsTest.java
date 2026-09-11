package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class AmbienceBedsTest {
    @Test void bedsHaveDeclaredLengthsAndNoBakedEventEnvelopes() {
        for (var bed : AmbienceBeds.Bed.values()) {
            float[] samples = AmbienceBeds.texture(bed, new Random(60600));
            assertEquals((int) (bed.seconds * 44100), samples.length);
            assertTrue(bed.seconds >= 17);
            double low = Double.POSITIVE_INFINITY, high = 0;
            for (int offset = 44100; offset + 22050 < samples.length; offset += 22050) {
                double sum = 0;
                for (int i = offset; i < offset + 22050; i++) sum += samples[i] * samples[i];
                double rms = Math.sqrt(sum / 22050);
                low = Math.min(low, rms); high = Math.max(high, rms);
            }
            assertTrue(high / low < 1.5, bed + " contains an event or periodic amplitude envelope");
            AudioSignalAssertions.clean(bed.name(), PcmAudio.prepare(samples));
        }
    }

    @Test void weatherLayersOccupyDifferentFrequencyBands() {
        float[] body = AmbienceBeds.texture(AmbienceBeds.Bed.Rain, new Random(42));
        float[] high = AmbienceBeds.texture(AmbienceBeds.Bed.RainHigh, new Random(42));
        double bodyRatio = AudioSignalAssertions.band(body, 44100, 5000, 10000)
                / AudioSignalAssertions.band(body, 44100, 200, 2000);
        double highRatio = AudioSignalAssertions.band(high, 44100, 5000, 10000)
                / AudioSignalAssertions.band(high, 44100, 200, 2000);
        assertTrue(highRatio > bodyRatio * 2, "rain sizzle must be spectrally distinct");
        float[] wind = AmbienceBeds.texture(AmbienceBeds.Bed.WindHigh, new Random(42));
        assertTrue(AudioSignalAssertions.band(wind, 44100, 1200, 6500)
                > AudioSignalAssertions.band(wind, 44100, 40, 180), "wind needs air above rumble");
    }

    @Test void rainIntensityChangesTimbreEvenAtEqualChannelGain() {
        float drizzle = AmbientEvents.layerWeight(6, 0, 1) / AmbientEvents.layerWeight(0, 0, 1);
        float storm = AmbientEvents.layerWeight(6, 1, 1) / AmbientEvents.layerWeight(0, 1, 1);
        assertTrue(drizzle > storm * 10, "intensity must alter the sizzle/body ratio");
    }

    @Test void detailsHaveFiniteAttacksAndDeclaredDurations() {
        for (int i = 0; i < 4; i++) {
            float[] event = AmbienceBeds.event(i, new Random(42));
            assertEquals((int) (AmbienceBeds.EVENT_SECONDS[i] * 44100), event.length);
            assertEquals(0, event[0], 0.00001);
            AudioSignalAssertions.clean("detail " + i, PcmAudio.prepare(event));
        }
    }

    @Test void sixtySecondsOfDetailsAreIrregularAndFrameBounded() {
        AmbientEvents events = new AmbientEvents(new Random(42));
        float[] gains = {1, 1, 1, 1, 1, 1};
        ArrayList<Integer> rainFrames = new ArrayList<>();
        for (int frame = 0; frame < 600; frame++) {
            int currentFrame = frame;
            int[] count = {0};
            events.update(0.1f, gains, kind -> { count[0]++; if (kind == 0) rainFrames.add(currentFrame); });
            assertTrue(count[0] <= 4);
        }
        assertTrue(rainFrames.size() > 100);
        assertTrue(java.util.stream.IntStream.range(1, rainFrames.size())
                .map(i -> rainFrames.get(i) - rainFrames.get(i - 1)).distinct().count() > 6);
        events.reset();
        assertEquals(0.75f, events.gust());
        events.update(999, new float[6], kind -> fail("inactive ambience emitted a detail"));
    }
}
