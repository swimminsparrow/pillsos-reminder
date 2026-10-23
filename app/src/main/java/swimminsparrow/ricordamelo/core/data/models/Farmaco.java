package swimminsparrow.ricordamelo.core.data.models;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

public class Farmaco
    {
        
        private int id;
        private String nome;
        private List<String> ore;
        private List<String> minuti;
        private String imagePath;
        private List<Integer> days;
        private long whenToFinish;
        private int forever;

        public Farmaco(int id, String nome, List<String> ore, List<String> minuti, String imagePath, List<Integer> days, long whenToFinish,int forever)
        {
            this.id = id;
            this.nome = nome;
            this.ore = ore;
            this.minuti = minuti;
            this.imagePath = imagePath;
            this.days = days;
            this.whenToFinish = whenToFinish;
            this.forever = forever;
        }

        public Farmaco(String nome, List<String> ore, List<String> minuti, String imagePath, List<Integer> days, long whenToFinish,int forever)
        {
            this.nome = nome;
            this.ore = ore;
            this.minuti = minuti;
            this.imagePath = imagePath;
            this.days = days;
            this.whenToFinish = whenToFinish;
            this.forever = forever;
        }

        public Farmaco(){

        }

        public static class FarmacoTable {
            public static final String TABLE_NAME = "AvvertimiTable";
            public static final String ID           = "ID";
            public static final String NOME         = "Nome";
            public static final String ORE          = "Ore";
            public static final String MINUTI       = "Minuti";
            public static final String DAYS         = "Days";
            public static final String IMAGEPATH    = "ImagePath";
            public static final String WHENTOFINISH = "WhenToFinish";
            public static final String FOREVER      = "Forever";

            public static final String SQL_CREATE =
                    "CREATE TABLE " + TABLE_NAME + " (" +
                            ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +                            NOME + " TEXT," + ORE + " TEXT," +
                            MINUTI + " TEXT," + DAYS + " TEXT," +
                            IMAGEPATH + " TEXT," +
                            WHENTOFINISH + " INTEGER," +
                            FOREVER + " INTEGER)";
        }

        /**
         * This Function reorders hours and Minutes of a farmaco object before saving Time (hh:mm) in the DB.
         * The algo implemented is a Bubble Sort
         */
        public void ordinaOrari() {
            int i = 0;
            while (i < this.ore.size() - 1) {
                if (Integer.parseInt(this.ore.get(i)) > Integer.parseInt(this.ore.get(i + 1))) {
                    String temp = this.ore.get(i);
                    this.ore.remove(i);
                    this.ore.add(i, this.ore.get(i));
                    this.ore.remove(i + 1);
                    this.ore.add(i + 1, temp);
                    temp = this.minuti.get(i);
                    this.minuti.remove(i);
                    this.minuti.add(i, this.minuti.get(i));
                    this.minuti.remove(i + 1);
                    this.minuti.add(i + 1, temp);
                }
                i++;
            }
        }

        /**
         * Returns the epoch-millisecond timestamp of the next scheduled alarm for
         * the farmaco at {@code pos}, or 0 if none is found.
         */
        public long getNextAlarm() {
            long nextAlarm = 0;
            GregorianCalendar now          = (GregorianCalendar) GregorianCalendar.getInstance();
            GregorianCalendar finishCal    = new GregorianCalendar();
            finishCal.setTimeInMillis(getWhenToFinish());

            boolean active = now.compareTo(finishCal) <= 0 || isForever();
            if (!active) return 0;

            int hourCount = getHourSize();
            for (int j = 0; j < hourCount; j++) {
                int hour    = Integer.parseInt(getHour(j));
                int minutes = Integer.parseInt(getMinute(j));

                for (int dayOffset = 0; dayOffset <= 7; dayOffset++) {
                    // Map calendar day-of-week (Sun=1…Sat=7) to our 0-based index
                    int todayIndex = now.get(Calendar.DAY_OF_WEEK) - 1; // 0=Sun … 6=Sat
                    int targetIndex = (todayIndex + dayOffset) % 7;

                    if (getDay( targetIndex) != 1) continue;

                    GregorianCalendar candidate = (GregorianCalendar) now.clone();
                    candidate.set(Calendar.HOUR_OF_DAY, hour);
                    candidate.set(Calendar.MINUTE, minutes);
                    candidate.set(Calendar.SECOND, 0);
                    candidate.set(Calendar.MILLISECOND, 0);
                    candidate.add(Calendar.DAY_OF_YEAR, dayOffset);

                    // Skip if already in the past
                    if (candidate.compareTo(now) <= 0) continue;

                    // Skip if beyond finish date (and not forever)
                    if (!isForever() && candidate.compareTo(finishCal) > 0) continue;

                    long ms = candidate.getTimeInMillis();
                    if (nextAlarm == 0 || ms < nextAlarm) {
                        nextAlarm = ms;
                    }
                }
            }
            return nextAlarm;
        }

        /** Formats the next alarm time as {@code "HH:mm"}, or {@code "--:--"} if none. */
        public String getNextTimeFormatted() {
            long millis = getNextAlarm();
            if (millis == 0) return "--:--";
            Date date = new Date(millis);
            return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(date);
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public List<String> getOre() {
            return ore;
        }

        public void setOre(List<String> ore) {
            this.ore = ore;
        }

        public List<String> getMinuti() {
            return minuti;
        }

        public void setMinuti(List<String> minuti) {
            this.minuti = minuti;
        }

        public String getImagePath() {
            return imagePath;
        }

        public void setImagePath(String imagePath) {
            this.imagePath = imagePath;
        }

        public List<Integer> getDays() {
            return days;
        }

        public void setDays(List<Integer> days) {
            this.days = days;
        }

        public long getWhenToFinish() {
            return whenToFinish;
        }

        public void setWhenToFinish(long whenToFinish) {
            this.whenToFinish = whenToFinish;
        }

        public int getForever() {
            return forever;
        }

        public void setForever(int forever) {
            this.forever = forever;
        }

        public boolean isForever()             { return forever == 1; }

        public int    getDay(int day)          { return days.get(day); }
        public String getHour(int i)           { return ore.get(i); }
        public String getMinute(int i)         { return minuti.get(i); }
        public int    getHourSize()            { return ore.size(); }

        public boolean isEveryDayAlarm()       { return !days.contains(0); }
    }
