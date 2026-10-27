package swimminsparrow.ricordamelo.ui.alarms;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import swimminsparrow.ricordamelo.core.utils.ImageUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.models.Farmaco;

public class AlarmAdapter extends BaseAdapter {

    private Context mContext;
    private List<Farmaco> farmaciList;
    private String[] daysLabels;

    private static class ViewHolder {
        com.google.android.material.card.MaterialCardView cardElemento;
        TextView textnome;
        ImageView icon;
        TextView textorari;
        TextView mDays;
        TextView el_t4;
        TextView textNextAlarm;
        ImageView alarmIcon;
        View pillContainer;
    }

    public AlarmAdapter(Context context) {
        this.mContext = context;
        this.farmaciList = new ArrayList<>();
        this.daysLabels = swimminsparrow.ricordamelo.core.utils.DateTimeUtils.getLocalizedShortDays();
    }

    public void setFarmaciList(List<Farmaco> farmaci) {
        this.farmaciList.clear();
        this.farmaciList.addAll(farmaci);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return farmaciList.size();
    }

    @Override
    public Object getItem(int position) {
        return farmaciList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return farmaciList.get(position).getId();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.elementogriglia, parent, false);

            holder = new ViewHolder();
            holder.cardElemento = convertView.findViewById(R.id.cardElemento);
            holder.textnome = convertView.findViewById(R.id.textnome);
            holder.icon = convertView.findViewById(R.id.mFoto);
            holder.textorari = convertView.findViewById(R.id.textView5);
            holder.mDays = convertView.findViewById(R.id.el_days);
            holder.el_t4 = convertView.findViewById(R.id.el_t4);
            holder.textNextAlarm = convertView.findViewById(R.id.mTextNextAlarm);
            holder.alarmIcon = convertView.findViewById(R.id.alarmIcon);
            holder.pillContainer = convertView.findViewById(R.id.rel1);

            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        Farmaco farmaco = farmaciList.get(position);

        // Set nome
        holder.textnome.setText(farmaco.getNome());

        // Set orari (format as "HH:MM • HH:MM")
        holder.textorari.setText(formatTimes(farmaco));

        // Set giorni (format as "Lun, Mar, Mer" or "Ogni giorno")
        holder.mDays.setText(formatDays(farmaco));

        // Set image
        loadImage(farmaco.getImagePath(), holder.icon);

        // Set next alarm info and expired status
        NextAlarmInfo nextAlarmInfo = calculateNextAlarmInfo(farmaco);
        holder.textNextAlarm.setText(nextAlarmInfo.text);

        // Apply expired styling if needed
        if (nextAlarmInfo.isExpired) {
            applyExpiredStyle(holder);
        } else {
            applyActiveStyle(holder);
        }

        return convertView;
    }

    private String formatTimes(Farmaco farmaco) {
        StringBuilder times = new StringBuilder();
        for (int i = 0; i < farmaco.getHourSize(); i++) {
            if (i > 0) {
                times.append(" • ");
            }
            times.append(String.format(Locale.ITALY, "%02d:%02d",
                    Integer.parseInt(farmaco.getHour(i)),
                    Integer.parseInt(farmaco.getMinute(i))));
        }
        return times.toString();
    }

    private String formatDays(Farmaco farmaco) {
        if (farmaco.isEveryDayAlarm()) {
            return mContext.getString(R.string.allDays);
        }

        StringBuilder days = new StringBuilder();
        List<Integer> daysList = farmaco.getDays();

        for (int i = 0; i < daysList.size(); i++) {
            if (daysList.get(i) == 1) {
                if (days.length() > 0) {
                    days.append(", ");
                }
                days.append(daysLabels[i]);
            }
        }

        return days.toString();
    }

    private void loadImage(String imagePath, ImageView imageView) {
        if (imagePath == null) {
            imageView.setImageResource(R.drawable.default_pill);
            return;
        }

        File file = new File(imagePath);
        if (!file.exists()) {
            // Try resolving as a legacy relative path inside the internal storage
            file = swimminsparrow.ricordamelo.core.utils.StorageUtils.getApplicationFile(mContext, imagePath);
        }

        if (file.exists()) {
            try {
                Bitmap bitmap = ImageUtils.loadBitmapWithExifRotation(file, 200);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                } else {
                    imageView.setImageResource(R.drawable.default_pill);
                }
            } catch (Exception e) {
                imageView.setImageResource(R.drawable.default_pill);
            }
        } else {
            imageView.setImageResource(R.drawable.default_pill);
        }
    }

    private NextAlarmInfo calculateNextAlarmInfo(Farmaco farmaco) {
        NextAlarmInfo info = new NextAlarmInfo();

        long nextAlarmMillis = farmaco.getNextAlarm();
        if (nextAlarmMillis == 0) {
            info.isExpired = true;
            info.text = mContext.getString(R.string.allarmescaduto);
            return info;
        }

        info.isExpired = false;
        GregorianCalendar actualTime = new GregorianCalendar();
        GregorianCalendar nextAlarm = new GregorianCalendar();
        nextAlarm.setTimeInMillis(nextAlarmMillis);

        GregorianCalendar tomorrowTime = new GregorianCalendar();
        tomorrowTime.add(Calendar.DAY_OF_YEAR, 1);

        String dayLabel;
        if (actualTime.get(Calendar.DAY_OF_YEAR) == nextAlarm.get(Calendar.DAY_OF_YEAR)
                && actualTime.get(Calendar.YEAR) == nextAlarm.get(Calendar.YEAR)) {
            dayLabel = mContext.getString(R.string.today);
        } else if (tomorrowTime.get(Calendar.DAY_OF_YEAR) == nextAlarm.get(Calendar.DAY_OF_YEAR)
                && tomorrowTime.get(Calendar.YEAR) == nextAlarm.get(Calendar.YEAR)) {
            dayLabel = mContext.getString(R.string.tomorrow);
        } else {
            String fullDayName = nextAlarm.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
            if (fullDayName != null && !fullDayName.isEmpty()) {
                dayLabel = Character.toUpperCase(fullDayName.charAt(0)) + fullDayName.substring(1);
            } else {
                int giorno = nextAlarm.get(Calendar.DAY_OF_WEEK) - 1;
                dayLabel = daysLabels[giorno];
            }
        }

        info.text = dayLabel + " " + farmaco.getNextTimeFormatted();
        return info;
    }

    private void applyActiveStyle(ViewHolder holder) {
        holder.cardElemento.setCardBackgroundColor(ContextCompat.getColor(mContext, android.R.color.white));
        holder.cardElemento.setStrokeColor(android.graphics.Color.parseColor("#E8ECE9"));
        holder.textnome.setTextColor(android.graphics.Color.parseColor("#111827"));
        holder.mDays.setTextColor(android.graphics.Color.parseColor("#546E7A"));
        holder.textorari.setTextColor(android.graphics.Color.parseColor("#78909C"));
        if (holder.pillContainer != null) {
            holder.pillContainer.setBackgroundResource(R.drawable.bg_next_alarm_pill);
        }
        holder.el_t4.setTextColor(ContextCompat.getColor(mContext, R.color.defaultColor));
        holder.textNextAlarm.setTextColor(ContextCompat.getColor(mContext, R.color.defaultColorDark));
        holder.alarmIcon.setColorFilter(ContextCompat.getColor(mContext, R.color.defaultColor));
    }

    private void applyExpiredStyle(ViewHolder holder) {
        holder.cardElemento.setCardBackgroundColor(android.graphics.Color.parseColor("#FFFDFD"));
        holder.cardElemento.setStrokeColor(ContextCompat.getColor(mContext, R.color.red_elemento_griglia));
        holder.textnome.setTextColor(ContextCompat.getColor(mContext, R.color.red_elemento_griglia));
        holder.mDays.setTextColor(ContextCompat.getColor(mContext, R.color.redButtonPressed));
        holder.textorari.setTextColor(ContextCompat.getColor(mContext, R.color.redButtonPressed));
        if (holder.pillContainer != null) {
            holder.pillContainer.setBackgroundResource(R.drawable.bg_next_alarm_pill_expired);
        }
        holder.el_t4.setTextColor(ContextCompat.getColor(mContext, R.color.red_elemento_griglia));
        holder.textNextAlarm.setTextColor(ContextCompat.getColor(mContext, R.color.red_elemento_griglia));
        holder.alarmIcon.setColorFilter(ContextCompat.getColor(mContext, R.color.red_elemento_griglia));
    }

    // Helper class for next alarm info
    private static class NextAlarmInfo {
        String text;
        boolean isExpired;
    }
}