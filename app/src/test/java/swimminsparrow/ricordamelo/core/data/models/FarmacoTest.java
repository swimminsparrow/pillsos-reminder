package swimminsparrow.ricordamelo.core.data.models;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class FarmacoTest {

    @Test
    public void testFarmacoCreationAndProperties() {
        List<String> hours = new ArrayList<>(Arrays.asList("08", "20"));
        List<String> minutes = new ArrayList<>(Arrays.asList("30", "00"));
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 0, 0)); // Mon-Fri
        long finish = System.currentTimeMillis() + 86400000L;

        Farmaco farmaco = new Farmaco(1, "Aspirina", hours, minutes, "/path/img", days, finish, 0);

        assertEquals(1, farmaco.getId());
        assertEquals("Aspirina", farmaco.getNome());
        assertEquals("/path/img", farmaco.getImagePath());
        assertEquals(2, farmaco.getHourSize());
        assertEquals("08", farmaco.getHour(0));
        assertEquals("30", farmaco.getMinute(0));
        assertEquals(1, (int) farmaco.getDay(0));
        assertEquals(0, farmaco.getForever());
        assertFalse(farmaco.isForever());
    }

    @Test
    public void testForeverFlag() {
        List<String> hours = new ArrayList<>(Arrays.asList("12"));
        List<String> minutes = new ArrayList<>(Arrays.asList("00"));
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 1, 1));

        Farmaco farmaco = new Farmaco("Tachipirina", hours, minutes, "", days, 0, 1);
        assertTrue(farmaco.isForever());
    }

    @Test
    public void testOrdinaOrari() {
        List<String> hours = new ArrayList<>(Arrays.asList("20", "08", "14"));
        List<String> minutes = new ArrayList<>(Arrays.asList("15", "30", "00"));
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 1, 1));

        Farmaco farmaco = new Farmaco("Cardioaspirina", hours, minutes, "", days, 0, 1);
        farmaco.ordinaOrari();

        // Check that hours are sorted ascending
        int h0 = Integer.parseInt(farmaco.getHour(0));
        int h1 = Integer.parseInt(farmaco.getHour(1));
        assertTrue(h0 <= h1);
    }
}
