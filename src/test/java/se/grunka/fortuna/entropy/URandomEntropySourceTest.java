package se.grunka.fortuna.entropy;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import se.grunka.fortuna.accumulator.EventAdder;
import se.grunka.fortuna.accumulator.EventScheduler;

import static org.junit.Assert.assertEquals;

public class URandomEntropySourceTest {

    private URandomEntropySource target;
    private int schedules, adds;

    @Before
    public void before() throws Exception {
        target = new URandomEntropySource();
        schedules = 0;
        adds = 0;
    }

    @Test
    public void shouldAddUptime() throws Exception {
        if (Files.exists(Paths.get("/dev/urandom"))) {
            target.event(
                    new EventScheduler() {
                        @Override
                        public void schedule(long delay, TimeUnit timeUnit) {
                            assertEquals(100, timeUnit.toMillis(delay));
                            schedules++;
                        }
                    },
                    new EventAdder() {
                        @Override
                        public void add(byte[] event) {
                            assertEquals(32, event.length);
                            adds++;
                        }
                    }
            );
            assertEquals(1, schedules);
            assertEquals(1, adds);
        }
    }
}
