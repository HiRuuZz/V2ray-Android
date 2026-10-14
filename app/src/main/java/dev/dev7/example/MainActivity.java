package dev.dev7.example;

import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_CONNECTION_STATE_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_DURATION_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA;
import static dev.dev7.lib.v2ray.utils.V2rayConstants.V2RAY_SERVICE_STATICS_BROADCAST_INTENT;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class MainActivity extends AppCompatActivity {

    private Button connection;
    private TextView connection_speed, connection_traffic, connection_time, server_delay, connected_server_delay, connection_mode, core_version, btn_add_config, config_preview_text;
    private EditText v2ray_config;
    private SharedPreferences sharedPreferences;
    private BroadcastReceiver v2rayBroadCastReceiver;

    @SuppressLint({"SetTextI18n", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (savedInstanceState == null) {
            V2rayController.init(this, R.drawable.ic_launcher, "V2ray Android");
            connection = findViewById(R.id.btn_connection);
            connection_speed = findViewById(R.id.connection_speed);
            connection_time = findViewById(R.id.connection_duration);
            connection_traffic = findViewById(R.id.connection_traffic);
            server_delay = findViewById(R.id.server_delay);
            connection_mode = findViewById(R.id.connection_mode);
            connected_server_delay = findViewById(R.id.connected_server_delay);
            v2ray_config = findViewById(R.id.v2ray_config);
            core_version = findViewById(R.id.core_version);
            btn_add_config = findViewById(R.id.btn_add_config);
            config_preview_text = findViewById(R.id.config_preview_text);
        }

        core_version.setText(V2rayController.getCoreVersion());
        
        // initialize shared preferences
        sharedPreferences = getSharedPreferences("conf", MODE_PRIVATE);
        String savedConfig = sharedPreferences.getString("v2ray_config", getDefaultConfig());
        v2ray_config.setText(savedConfig);
        
        if (!savedConfig.isEmpty()) {
            config_preview_text.setText(savedConfig.length() > 40 ? savedConfig.substring(0, 40) + "..." : savedConfig);
        }

        // Add Config Button Dialog (+)
        btn_add_config.setOnClickListener(v -> showImportOptionsDialog());

        // Connect / Disconnect Action
        connection.setOnClickListener(view -> {
            String currentConfig = v2ray_config.getText().toString();
            if (currentConfig.isEmpty()) {
                Toast.makeText(this, "Please import a config first!", Toast.LENGTH_SHORT).show();
                return;
            }
            sharedPreferences.edit().putString("v2ray_config", currentConfig).apply();
            if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
                V2rayController.startV2ray(this, "V2ray Server", currentConfig, null);
            } else {
                V2rayController.stopV2ray(this);
            }
        });

        // Connected server delay check
        connected_server_delay.setOnClickListener(view -> {
            connected_server_delay.setText("delay : measuring...");
            V2rayController.getConnectedV2rayServerDelay(this, delayResult -> runOnUiThread(() -> connected_server_delay.setText("delay : " + delayResult + "ms")));
        });

        // Server delay ping test button
        server_delay.setOnClickListener(view -> {
            String cfg = v2ray_config.getText().toString();
            if (cfg.isEmpty()) {
                Toast.makeText(this, "No config to test!", Toast.LENGTH_SHORT).show();
                return;
            }
            server_delay.setText("Testing...");
            new Handler().postDelayed(() -> server_delay.setText(V2rayController.getV2rayServerDelay(cfg) + "ms"), 200);
        });

        connection_mode.setOnClickListener(view -> {
            V2rayController.toggleConnectionMode();
            connection_mode.setText("connection mode : " + V2rayConfigs.serviceMode.toString());
        });

        // Check connection state when activity launch
        switch (V2rayController.getConnectionState()) {
            case CONNECTED:
                connection.setText("■");
                connected_server_delay.callOnClick();
                break;
            case DISCONNECTED:
                connection.setText("▶");
                break;
            case CONNECTING:
                connection.setText("...");
                break;
            default:
                break;
        }

        v2rayBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnUiThread(() -> {
                    connection_time.setText("time: " + Objects.requireNonNull(intent.getExtras()).getString(SERVICE_DURATION_BROADCAST_EXTRA));
                    connection_speed.setText(intent.getExtras().getString(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA) + " | " + intent.getExtras().getString(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA));
                    connection_traffic.setText("traffic: " + intent.getExtras().getString(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA) + " | " + intent.getExtras().getString(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA));
                    connection_mode.setText("connection mode : " + V2rayConfigs.serviceMode.toString());
                    switch ((V2rayConstants.CONNECTION_STATES) Objects.requireNonNull(intent.getExtras().getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA))) {
                        case CONNECTED:
                            connection.setText("■");
                            break;
                        case DISCONNECTED:
                            connection.setText("▶");
                            connected_server_delay.setText("connected server delay : wait");
                            break;
                        case CONNECTING:
                            connection.setText("...");
                            break;
                        default:
                            break;
                    }
                });
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(v2rayBroadCastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT), RECEIVER_EXPORTED);
        } else {
            registerReceiver(v2rayBroadCastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT));
        }
    }

    private void showImportOptionsDialog() {
        String[] options = {"Import from Clipboard", "Manually Type"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Configuration");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                importFromClipboard();
            } else if (which == 1) {
                showManualInputDialog();
            }
        });
        builder.show();
    }

    private void importFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() > 0) {
                CharSequence text = clipData.getItemAt(0).getText();
                if (text != null) {
                    String pastedConfig = text.toString().trim();
                    v2ray_config.setText(pastedConfig);
                    sharedPreferences.edit().putString("v2ray_config", pastedConfig).apply();
                    config_preview_text.setText(pastedConfig.length() > 40 ? pastedConfig.substring(0, 40) + "..." : pastedConfig);
                    Toast.makeText(this, "Config Imported from Clipboard!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        Toast.makeText(this, "Clipboard is empty!", Toast.LENGTH_SHORT).show();
    }

    private void showManualInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Manual Config Input");

        final EditText input = new EditText(this);
        input.setHint("Paste vmess:// or vless:// link here");
        input.setMinLines(4);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String manualConfig = input.getText().toString().trim();
            if (!manualConfig.isEmpty()) {
                v2ray_config.setText(manualConfig);
                sharedPreferences.edit().putString("v2ray_config", manualConfig).apply();
                config_preview_text.setText(manualConfig.length() > 40 ? manualConfig.substring(0, 40) + "..." : manualConfig);
                Toast.makeText(this, "Config Saved Successfully!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Config cannot be empty!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    public static String getDefaultConfig() {
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (v2rayBroadCastReceiver != null) {
            unregisterReceiver(v2rayBroadCastReceiver);
        }
    }
}
