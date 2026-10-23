package swimminsparrow.ricordamelo.core.utils;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Locale;

/**
 * Utility methods for date and time formatting and localized day names.
 */
public final class DateTimeUtils {

    private DateTimeUtils() {
        // Utility class
    }

    /**
     * Returns an array of 7 localized short day names starting from Sunday (index 0) to Saturday (index 6),
     * with the first character capitalized and any trailing punctuation removed.
     */
    public static String[] getLocalizedShortDays() {
        return getLocalizedShortDays(Locale.getDefault());
    }

    /**
     * Returns an array of 7 localized short day names for the specified locale,
     * starting from Sunday (index 0) to Saturday (index 6).
     */
    public static String[] getLocalizedShortDays(Locale locale) {
        if (locale == null) {
            locale = Locale.getDefault();
        }
        DateFormatSymbols dfs = DateFormatSymbols.getInstance(locale);
        String[] shortWeekdays = dfs.getShortWeekdays(); // 1-based index (Calendar.SUNDAY = 1)
        int[] calDays = {
                Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY,
                Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
        };

        String[] result = new String[7];
        for (int i = 0; i < 7; i++) {
            String raw = shortWeekdays[calDays[i]];
            if (raw != null && !raw.isEmpty()) {
                // Remove trailing punctuation like dots if present (e.g. "lun." -> "lun")
                if (raw.endsWith(".")) {
                    raw = raw.substring(0, raw.length() - 1);
                }
                // Capitalize first letter
                result[i] = raw.substring(0, 1).toUpperCase(locale) + raw.substring(1);
            } else {
                result[i] = "";
            }
        }
        return result;
    }
}
