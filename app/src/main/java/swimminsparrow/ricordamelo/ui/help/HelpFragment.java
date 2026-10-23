package swimminsparrow.ricordamelo.ui.help;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import swimminsparrow.ricordamelo.core.utils.BatteryOptimizationsHelper;
import swimminsparrow.ricordamelo.R;


public class HelpFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_help, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button msendMail = view.findViewById(R.id.faq4_b);
        Button mVerify = view.findViewById(R.id.faq3_b);

        msendMail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
                emailIntent.setData(Uri.parse("mailto: swimminsparrow@gmail.com"));
                startActivity(Intent.createChooser(emailIntent, "Send feedback"));
            }
        });

        mVerify.setOnClickListener(v -> BatteryOptimizationsHelper.openBatteryOptimizationSettings(requireContext()));

    }


}
