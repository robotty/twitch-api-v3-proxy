package de.zwb3.apiproxy;

import org.junit.Test;

import java.time.Duration;

import static org.junit.Assert.assertEquals;

public class DateFormatTest {

    @Test
    public void testUptimeFormatter() {
        assertEquals("1 day, 5 hours, 3 minutes and 20 seconds", ApiResponseController.formatUptimeDuration(Duration.ofMillis(104600 * 1000)));
        assertEquals("5 hours, 5 minutes and 59 seconds", ApiResponseController.formatUptimeDuration(Duration.ofMillis(18359 * 1000)));
        assertEquals("1 day and 0.1 second", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400100)));
        assertEquals("1 day and 0.1 second", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400099)));
        assertEquals("1 day and 0.1 second", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400050)));
        assertEquals("1 day and 0.2 seconds", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400200)));
        assertEquals("1 day", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400049)));
        assertEquals("1 day and 1 second", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400999)));
        assertEquals("1 day and 1 second", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400950)));
        assertEquals("1 day and 2 seconds", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86402000)));
        assertEquals("1 day and 2.1 seconds", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86402100)));
        assertEquals("1 day and 0.9 seconds", ApiResponseController.formatUptimeDuration(Duration.ofMillis(86400949)));
    }
}
