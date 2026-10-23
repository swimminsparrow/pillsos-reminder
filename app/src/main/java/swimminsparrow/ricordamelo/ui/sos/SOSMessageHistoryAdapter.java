package swimminsparrow.ricordamelo.ui.sos;

import android.content.Context;
import android.graphics.Color;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.data.models.SOSMessage;

public class SOSMessageHistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_DISCLAIMER = 3;

    private final Context context;
    private final List<SOSMessage> messages;
    private final SimpleDateFormat todayFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat otherDayFormat = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());

    public SOSMessageHistoryAdapter(Context context, List<SOSMessage> messages) {
        this.context = context;
        this.messages = messages != null ? new java.util.ArrayList<>(messages) : new java.util.ArrayList<>();
    }

    public void updateMessages(List<SOSMessage> newMessages) {
        final List<SOSMessage> safeNew = newMessages != null ? newMessages : java.util.Collections.emptyList();
        androidx.recyclerview.widget.DiffUtil.DiffResult diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return messages.isEmpty() ? 0 : messages.size() + 1;
            }

            @Override
            public int getNewListSize() {
                return safeNew.isEmpty() ? 0 : safeNew.size() + 1;
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 && newItemPosition == 0) return true;
                if (oldItemPosition == 0 || newItemPosition == 0) return false;
                SOSMessage oldMsg = messages.get(oldItemPosition - 1);
                SOSMessage newMsg = safeNew.get(newItemPosition - 1);
                return oldMsg.getId() == newMsg.getId();
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                if (oldItemPosition == 0 && newItemPosition == 0) return true;
                if (oldItemPosition == 0 || newItemPosition == 0) return false;
                SOSMessage oldMsg = messages.get(oldItemPosition - 1);
                SOSMessage newMsg = safeNew.get(newItemPosition - 1);
                return oldMsg.getId() == newMsg.getId() &&
                        java.util.Objects.equals(oldMsg.getMessage(), newMsg.getMessage()) &&
                        oldMsg.getTimestamp() == newMsg.getTimestamp() &&
                        oldMsg.isAckDelivered() == newMsg.isAckDelivered() &&
                        java.util.Objects.equals(oldMsg.getDirection(), newMsg.getDirection());
            }
        });
        this.messages.clear();
        this.messages.addAll(safeNew);
        diffResult.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return VIEW_TYPE_DISCLAIMER;
        }
        int messageIndex = position - 1;
        if (messages != null && messageIndex >= 0 && messageIndex < messages.size()) {
            SOSMessage msg = messages.get(messageIndex);
            return (msg != null && msg.isSent()) ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
        }
        return VIEW_TYPE_RECEIVED;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_DISCLAIMER) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_sos_message_disclaimer, parent, false);
            return new DisclaimerViewHolder(view);
        }
        int layoutRes = (viewType == VIEW_TYPE_SENT)
                ? R.layout.item_sos_message_sent
                : R.layout.item_sos_message_received;
        View view = LayoutInflater.from(context).inflate(layoutRes, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof MessageViewHolder && messages != null && position > 0) {
            int messageIndex = position - 1;
            if (messageIndex < messages.size()) {
                MessageViewHolder msgHolder = (MessageViewHolder) holder;
                SOSMessage message = messages.get(messageIndex);
                msgHolder.tvMessageText.setText(message.getMessage());

                if (message.getTimestamp() > 0) {
                    if (DateUtils.isToday(message.getTimestamp())) {
                        msgHolder.tvTimestamp.setText(todayFormat.format(new Date(message.getTimestamp())));
                    } else {
                        msgHolder.tvTimestamp.setText(otherDayFormat.format(new Date(message.getTimestamp())));
                    }
                } else {
                    msgHolder.tvTimestamp.setText(message.getFormattedTimestamp());
                }

                if (msgHolder.ivSentStatus != null) {
                    if (message.isAckDelivered()) {
                        msgHolder.ivSentStatus.setImageResource(R.drawable.ic_double_check);
                        msgHolder.ivSentStatus.setColorFilter(Color.parseColor("#34B7F1"));
                    } else {
                        msgHolder.ivSentStatus.setImageResource(R.drawable.baseline_done_24);
                        msgHolder.ivSentStatus.setColorFilter(Color.parseColor("#D4F4C6"));
                    }
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return (messages != null && !messages.isEmpty()) ? messages.size() + 1 : 0;
    }

    public static class DisclaimerViewHolder extends RecyclerView.ViewHolder {
        public DisclaimerViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView tvTimestamp;
        final TextView tvMessageText;
        final ImageView ivSentStatus;

        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tvMessageTimestamp);
            tvMessageText = itemView.findViewById(R.id.tvMessageText);
            ivSentStatus = itemView.findViewById(R.id.ivSentStatus);
        }
    }
}
