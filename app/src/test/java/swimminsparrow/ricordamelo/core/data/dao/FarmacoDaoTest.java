package swimminsparrow.ricordamelo.core.data.dao;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import swimminsparrow.ricordamelo.core.data.models.Farmaco;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class FarmacoDaoTest {

    private Context context;
    private FarmacoDao dao;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        dao = FarmacoDao.getInstance(context);
        // Clear existing data from previous tests
        List<Farmaco> existing = dao.getAll();
        for (Farmaco f : existing) {
            dao.delete(f.getId());
        }
    }

    @Test
    public void testInsertAndGetById() {
        List<String> hours = new ArrayList<>(Arrays.asList("08", "20"));
        List<String> minutes = new ArrayList<>(Arrays.asList("30", "00"));
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 0, 0));

        Farmaco farmaco = new Farmaco("Ibuprofene", hours, minutes, "/img/path.jpg", days, 0, 1);
        long newId = dao.insert(farmaco);
        assertTrue(newId > 0);

        Farmaco retrieved = dao.getById((int) newId);
        assertNotNull(retrieved);
        assertEquals("Ibuprofene", retrieved.getNome());
        assertEquals("/img/path.jpg", retrieved.getImagePath());
        assertEquals(2, retrieved.getHourSize());
        assertEquals("08", retrieved.getHour(0));
        assertEquals("30", retrieved.getMinute(0));
        assertTrue(retrieved.isForever());
    }

    @Test
    public void testGetAllAndCount() {
        assertEquals(0, dao.getAll().size());

        List<String> hours = new ArrayList<>(Arrays.asList("10"));
        List<String> minutes = new ArrayList<>(Arrays.asList("00"));
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 1, 1));

        Farmaco f1 = new Farmaco("Farmaco A", hours, minutes, "", days, 0, 1);
        Farmaco f2 = new Farmaco("Farmaco B", hours, minutes, "", days, 0, 1);

        dao.insert(f1);
        dao.insert(f2);

        assertEquals(2, dao.getAll().size());
        List<Farmaco> list = dao.getAll();
        assertEquals(2, list.size());
        assertTrue(dao.existsByName("Farmaco A"));
        assertFalse(dao.existsByName("Farmaco Z"));
    }

    @Test
    public void testDelete() {
        List<String> hours = new ArrayList<>(Arrays.asList("12"));
        List<String> minutes = new ArrayList<>(Arrays.asList("00"));
        List<Integer> days = new ArrayList<>(Arrays.asList(1, 1, 1, 1, 1, 1, 1));

        Farmaco f = new Farmaco("Da Cancellare", hours, minutes, "", days, 0, 1);
        long id = dao.insert(f);

        assertEquals(1, dao.getAll().size());
        dao.delete((int) id);
        assertEquals(0, dao.getAll().size());
        assertNull(dao.getById((int) id));
    }
}
