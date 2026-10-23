package swimminsparrow.ricordamelo.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import swimminsparrow.ricordamelo.core.sos.ConnectionRequest;
import swimminsparrow.ricordamelo.core.sos.ConnectionRequestManager;
import swimminsparrow.ricordamelo.core.utils.CheckPermissionsHandler;
import swimminsparrow.ricordamelo.core.utils.PermissionCallback;

public class BaseActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CAMERA = 1;
    private static final int REQUEST_PERMISSION_POST_NOTIFICATIONS = 2;

    private PermissionCallback permissionCallback;

    private final BroadcastReceiver connectionRequestReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && ConnectionRequestManager.ACTION_CONNECTION_REQUEST_RECEIVED.equals(intent.getAction())) {
                ConnectionRequest req = ConnectionRequest.fromIntent(intent);
                if (req != null) {
                    ConnectionRequestManager.showApprovalDialog(BaseActivity.this, req);
                }
            }
        }
    };

    public static volatile boolean isForeground = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConnectionRequestManager.startListening(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        isForeground = true;
        IntentFilter filter = new IntentFilter(ConnectionRequestManager.ACTION_CONNECTION_REQUEST_RECEIVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionRequestReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(connectionRequestReceiver, filter);
        }
        ConnectionRequestManager.checkPendingRequests(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isForeground = false;
        try {
            unregisterReceiver(connectionRequestReceiver);
        } catch (Exception ignored) {}
    }

    public void checkCameraPermission(PermissionCallback callback) {
        if (CheckPermissionsHandler.isCameraGranted(BaseActivity.this)) {
            // Permission already granted, execute callback immediately
            callback.onPermissionGranted();
        } else {
            // Store callback and request permission
            this.permissionCallback = callback;
            ActivityCompat.requestPermissions(BaseActivity.this,
                    new String[]{Manifest.permission.CAMERA}, REQUEST_PERMISSION_CAMERA);
        }
    }

    public void checkNotificationPermission(PermissionCallback callback) {
        if (CheckPermissionsHandler.isNotificationGranted(BaseActivity.this)) {
            // Permission already granted, execute callback immediately
            callback.onPermissionGranted();
        } else {
            // Store callback and request permission
            this.permissionCallback = callback;
            ActivityCompat.requestPermissions(BaseActivity.this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_PERMISSION_POST_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            // Permission granted
            if (permissionCallback != null) {
                permissionCallback.onPermissionGranted();
                permissionCallback = null;
            }
        } else {
            // Permission denied
            if (permissionCallback != null) {
                permissionCallback.onPermissionDenied();
                permissionCallback = null;
            }
        }
    }

}

