package swimminsparrow.ricordamelo.core.utils;

import org.junit.Test;
import java.util.Locale;

import static org.junit.Assert.*;

public class DateTimeUtilsTest {

    @Test
    public void testGetLocalizedShortDaysItalian() {
        String[] days = DateTimeUtils.getLocalizedShortDays(Locale.ITALIAN);
        assertNotNull(days);
        assertEquals(7, days.length);

        // Should start with Sunday and end with Saturday
        // In Italian: Dom, Lun, Mar, Mer, Gio, Ven, Sab
        for (String day : days) {
            assertNotNull(day);
            assertFalse(day.isEmpty());
            assertTrue("Expected uppercase first char: " + day, Character.isUpperCase(day.charAt(0)));
            assertFalse("Expected no trailing period: " + day, day.endsWith("."));
        }

        assertTrue("Expected Italian Sunday 'Dom...': " + days[0], days[0].startsWith("Dom"));
        assertTrue("Expected Italian Monday 'Lun...': " + days[1], days[1].startsWith("Lun"));
        assertTrue("Expected Italian Saturday 'Sab...': " + days[6], days[6].startsWith("Sab"));
    }

    @Test
    public void testGetLocalizedShortDaysEnglish() {
        String[] days = DateTimeUtils.getLocalizedShortDays(Locale.ENGLISH);
        assertNotNull(days);
        assertEquals(7, days.length);

        for (String day : days) {
            assertNotNull(day);
            assertFalse(day.isEmpty());
            assertTrue("Expected uppercase first char: " + day, Character.isUpperCase(day.charAt(0)));
            assertFalse("Expected no trailing period: " + day, day.endsWith("."));
        }

        assertTrue("Expected English Sunday 'Sun': " + days[0], days[0].startsWith("Sun"));
        assertTrue("Expected English Monday 'Mon': " + days[1], days[1].startsWith("Mon"));
        assertTrue("Expected English Saturday 'Sat': " + days[6], days[6].startsWith("Sat"));
    }
}
