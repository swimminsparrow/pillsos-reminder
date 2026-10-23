package swimminsparrow.ricordamelo.ui.sos;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;

import swimminsparrow.ricordamelo.R;
import swimminsparrow.ricordamelo.core.sos.SOSContact;
import swimminsparrow.ricordamelo.core.utils.ImageUtils;

public class SOSContactsAdapter extends RecyclerView.Adapter<SOSContactsAdapter.ViewHolder> {

    private static final String TAG = "SOSContactsAdapter";
    private final Context context;
    private final List<SOSContact> contacts = new ArrayList<>();

    public SOSContactsAdapter(Context context) {
        this.context = context;
    }

    public void setContacts(List<SOSContact> newContacts) {
        androidx.recyclerview.widget.DiffUtil.DiffResult diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(new androidx.recyclerview.widget.DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return contacts.size();
            }

            @Override
            public int getNewListSize() {
                return newContacts != null ? newContacts.size() : 0;
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                SOSContact oldItem = contacts.get(oldItemPosition);
                SOSContact newItem = newContacts.get(newItemPosition);
                return oldItem.getTopic() != null && oldItem.getTopic().equals(newItem.getTopic());
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                SOSContact oldItem = contacts.get(oldItemPosition);
                SOSContact newItem = newContacts.get(newItemPosition);
                return java.util.Objects.equals(oldItem.getName(), newItem.getName()) &&
                        java.util.Objects.equals(oldItem.getLastMessage(), newItem.getLastMessage()) &&
                        oldItem.getLastMessageTimestamp() == newItem.getLastMessageTimestamp() &&
                        java.util.Objects.equals(oldItem.getAvatarBase64(), newItem.getAvatarBase64());
            }
        });
        this.contacts.clear();
        if (newContacts != null) {
            this.contacts.addAll(newContacts);
        }
        diffResult.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sos_contact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SOSContact contact = contacts.get(position);
        holder.tvName.setText(contact.getName());

        // Top-Right timestamp
        String timeStr = contact.getFormattedTimestamp();
        holder.tvTimestamp.setText(timeStr);
        holder.tvTimestamp.setVisibility(timeStr.isEmpty() ? View.GONE : View.VISIBLE);

        // Subtitle / Preview
        String lastMsg = contact.getLastMessage();
        if (lastMsg != null && !lastMsg.trim().isEmpty()) {
            holder.tvLastMessage.setText(lastMsg);
        } else {
            holder.tvLastMessage.setText(R.string.sos_mutual_active);
        }

        // Avatar
        ImageUtils.bindAvatar(holder.ivAvatar, contact.getAvatarBase64(), R.drawable.avatar);

        // Click to Open Contact Detail & History
        holder.itemView.setOnClickListener(v -> {
            try {
                Bundle args = new Bundle();
                args.putString(SOSMessageHistoryFragment.ARG_TOPIC, contact.getTopic());
                args.putString(SOSMessageHistoryFragment.ARG_NAME, contact.getName());
                args.putString(SOSMessageHistoryFragment.ARG_AVATAR, contact.getAvatarBase64());
                Navigation.findNavController(v).navigate(R.id.nav_sos_history, args);
            } catch (Exception e) {
                Log.e(TAG, "Error navigating to SOS history", e);
            }
        });
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ShapeableImageView ivAvatar;
        final TextView tvName;
        final TextView tvTimestamp;
        final TextView tvLastMessage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivAvatar);
            tvName = itemView.findViewById(R.id.tvContactName);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvLastMessage = itemView.findViewById(R.id.tvLastMessage);
        }
    }
}
