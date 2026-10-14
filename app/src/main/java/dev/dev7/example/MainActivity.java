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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Objects;

import dev.dev7.lib.v2ray.V2rayController;
import dev.dev7.lib.v2ray.utils.V2rayConfigs;
import dev.dev7.lib.v2ray.utils.V2rayConstants;

public class MainActivity extends AppCompatActivity {

    private Button connection;
    private TextView connection_speed, connection_traffic, connection_time, server_delay, connected_server_delay, connection_mode, core_version, btn_add_config, status_text;
    private EditText v2ray_config;
    private LinearLayout config_list_container;
    private SharedPreferences sharedPreferences;
    private BroadcastReceiver v2rayBroadCastReceiver;
    
    private ArrayList<ConfigItem> configList = new ArrayList<>();
    private int selectedConfigIndex = -1;

    public static class ConfigItem {
        String name;
        String content;

        public ConfigItem(String name, String content) {
            this.name = name;
            this.content = content;
        }
    }

    @SuppressLint({"SetTextI18n", "UnspecifiedRegisterReceiverFlag"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        if (savedInstanceState == null) {
            V2rayController.init(this, R.drawable.ic_launcher, "Ruu VPN");
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
            status_text = findViewById(R.id.status_text);
            config_list_container = findViewById(R.id.config_list_container);
        }

        core_version.setText(V2rayController.getCoreVersion());
        sharedPreferences = getSharedPreferences("conf_prefs", MODE_PRIVATE);
        
        loadConfigsFromPrefs();
        refreshConfigListUI();

        // Add Config Button Dialog (+)
        btn_add_config.setOnClickListener(v -> showImportOptionsDialog());

        // Connect / Disconnect Action Button
        connection.setOnClickListener(view -> {
            String currentConfig = v2ray_config.getText().toString();
            if (currentConfig.isEmpty()) {
                Toast.makeText(this, "Please select or import a config first!", Toast.LENGTH_SHORT).show();
                status_text.setText("Error: No config selected");
                return;
            }
            if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.DISCONNECTED) {
                status_text.setText("Connecting...");
                V2rayController.startV2ray(this, "Ruu VPN Server", currentConfig, null);
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
        updateConnectionStateUI(V2rayController.getConnectionState());

        v2rayBroadCastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnUiThread(() -> {
                    connection_time.setText("time: " + Objects.requireNonNull(intent.getExtras()).getString(SERVICE_DURATION_BROADCAST_EXTRA));
                    connection_speed.setText(intent.getExtras().getString(SERVICE_UPLOAD_SPEED_BROADCAST_EXTRA) + " | " + intent.getExtras().getString(SERVICE_DOWNLOAD_SPEED_BROADCAST_EXTRA));
                    connection_traffic.setText("traffic: " + intent.getExtras().getString(SERVICE_UPLOAD_TRAFFIC_BROADCAST_EXTRA) + " | " + intent.getExtras().getString(SERVICE_DOWNLOAD_TRAFFIC_BROADCAST_EXTRA));
                    connection_mode.setText("connection mode : " + V2rayConfigs.serviceMode.toString());
                    
                    V2rayConstants.CONNECTION_STATES state = (V2rayConstants.CONNECTION_STATES) intent.getExtras().getSerializable(SERVICE_CONNECTION_STATE_BROADCAST_EXTRA);
                    updateConnectionStateUI(state);
                });
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(v2rayBroadCastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT), RECEIVER_EXPORTED);
        } else {
            registerReceiver(v2rayBroadCastReceiver, new IntentFilter(V2RAY_SERVICE_STATICS_BROADCAST_INTENT));
        }
    }

    private void updateConnectionStateUI(V2rayConstants.CONNECTION_STATES state) {
        switch (state) {
            case CONNECTED:
                connection.setText("■");
                status_text.setText("Connected Success");
                connected_server_delay.callOnClick();
                break;
            case DISCONNECTED:
                connection.setText("▶");
                status_text.setText("Disconnected");
                connected_server_delay.setText("delay : wait");
                break;
            case CONNECTING:
                connection.setText("...");
                status_text.setText("Connecting...");
                break;
            default:
                break;
        }
    }

    private void loadConfigsFromPrefs() {
        configList.clear();
        String jsonStr = sharedPreferences.getString("config_list_json", "[]");
        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                configList.add(new ConfigItem(obj.getString("name"), obj.getString("content")));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        // Load active config if available
        String activeCfg = sharedPreferences.getString("active_config_content", "");
        v2ray_config.setText(activeCfg);
    }

    private void saveConfigsToPrefs() {
        JSONArray jsonArray = new JSONArray();
        try {
            for (ConfigItem item : configList) {
                JSONObject obj = new JSONObject();
                obj.put("name", item.name);
                obj.put("content", item.content);
                jsonArray.put(obj);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        sharedPreferences.edit().putString("config_list_json", jsonArray.toString()).apply();
    }

    private void refreshConfigListUI() {
        config_list_container.removeAllViews();
        
        if (configList.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No configs imported yet. Tap '+' to add.");
            emptyView.setTextColor(0xFFAAAAAA);
            emptyView.setTextSize(12);
            emptyView.setPadding(4, 8, 4, 8);
            config_list_container.addView(emptyView);
            return;
        }

        for (int i = 0; i < configList.size(); i++) {
            final int index = i;
            ConfigItem item = configList.get(index);

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(0xFF1E1E1E);
            card.setPadding(16, 16, 16, 16);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            cardParams.setMargins(0, 0, 0, 12);
            card.setLayoutParams(cardParams);

            // Title & Action Buttons Row
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameView = new TextView(this);
            nameView.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            nameView.setText(item.name);
            nameView.setTextColor(0xFFFFFFFF);
            nameView.setTextSize(15);
            nameView.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(nameView);

            // Share Button
            TextView btnShare = new TextView(this);
            btnShare.setText(" Share ");
            btnShare.setTextColor(0xFF00E5FF);
            btnShare.setTextSize(12);
            btnShare.setPadding(8, 4, 8, 4);
            btnShare.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Config", item.content);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Config copied to clipboard!", Toast.LENGTH_SHORT).show();
            });
            topRow.addView(btnShare);

            // Edit Button
            TextView btnEdit = new TextView(this);
            btnEdit.setText(" Edit ");
            btnEdit.setTextColor(0xFFFF9800);
            btnEdit.setTextSize(12);
            btnEdit.setPadding(8, 4, 8, 4);
            btnEdit.setOnClickListener(v -> showEditConfigDialog(index));
            topRow.addView(btnEdit);

            // Delete Button
            TextView btnDelete = new TextView(this);
            btnDelete.setText(" Del ");
            btnDelete.setTextColor(0xFFFF5252);
            btnDelete.setTextSize(12);
            btnDelete.setPadding(8, 4, 8, 4);
            btnDelete.setOnClickListener(v -> {
                if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.CONNECTED && v2ray_config.getText().toString().equals(item.content)) {
                    Toast.makeText(this, "Please stop VPN before deleting active config!", Toast.LENGTH_SHORT).show();
                    return;
                }
                configList.remove(index);
                saveConfigsToPrefs();
                refreshConfigListUI();
                Toast.makeText(this, "Config deleted!", Toast.LENGTH_SHORT).show();
            });
            topRow.addView(btnDelete);

            card.addView(topRow);

            // Snippet Preview
            TextView previewView = new TextView(this);
            previewView.setText(item.content.length() > 50 ? item.content.substring(0, 50) + "..." : item.content);
            previewView.setTextColor(0xFFAAAAAA);
            previewView.setTextSize(11);
            previewView.setPadding(0, 6, 0, 0);
            card.addView(previewView);

            // Click card to select config for connection
            card.setOnClickListener(v -> {
                if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.CONNECTED) {
                    Toast.makeText(this, "Please stop vpn connection before you select another config", Toast.LENGTH_LONG).show();
                    status_text.setText("Error: Stop VPN first");
                    return;
                }
                v2ray_config.setText(item.content);
                sharedPreferences.edit().putString("active_config_content", item.content).apply();
                Toast.makeText(this, "Selected: " + item.name, Toast.LENGTH_SHORT).show();
                status_text.setText("Ready: " + item.name);
            });

            config_list_container.addView(card);
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
                showManualInputDialog(-1);
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
                    String configName = "Config " + (configList.size() + 1);
                    configList.add(new ConfigItem(configName, pastedConfig));
                    saveConfigsToPrefs();
                    refreshConfigListUI();
                    
                    // Auto select if first
                    if (configList.size() == 1) {
                        v2ray_config.setText(pastedConfig);
                        sharedPreferences.edit().putString("active_config_content", pastedConfig).apply();
                    }
                    
                    Toast.makeText(this, "Config Imported Successfully!", Toast.LENGTH_SHORT).show();
                    status_text.setText("Ready");
                    return;
                }
            }
        }
        Toast.makeText(this, "Clipboard is empty!", Toast.LENGTH_SHORT).show();
        status_text.setText("Error: Clipboard empty");
    }

    private void showManualInputDialog(final int editIndex) {
        boolean isEdit = (editIndex != -1);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isEdit ? "Edit Configuration" : "Manual Config Input");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Config Name (e.g. Hutch Zoom)");
        if (isEdit) nameInput.setText(configList.get(editIndex).name);
        layout.addView(nameInput);

        final EditText contentInput = new EditText(this);
        contentInput.setHint("Paste vmess:// or vless:// link here");
        contentInput.setMinLines(4);
        if (isEdit) contentInput.setText(configList.get(editIndex).content);
        layout.addView(contentInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String content = contentInput.getText().toString().trim();
            
            if (name.isEmpty()) name = "Config " + (configList.size() + 1);
            
            if (!content.isEmpty()) {
                if (isEdit) {
                    configList.get(editIndex).name = name;
                    configList.get(editIndex).content = content;
                    Toast.makeText(this, "Config Updated!", Toast.LENGTH_SHORT).show();
                } else {
                    configList.add(new ConfigItem(name, content));
                    if (configList.size() == 1) {
                        v2ray_config.setText(content);
                        sharedPreferences.edit().putString("active_config_content", content).apply();
                    }
                    Toast.makeText(this, "Config Added Successfully!", Toast.LENGTH_SHORT).show();
                }
                saveConfigsToPrefs();
                refreshConfigListUI();
                status_text.setText("Ready");
            } else {
                Toast.makeText(this, "Config content cannot be empty!", Toast.LENGTH_SHORT).show();
                status_text.setText("Error: Empty content");
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void showEditConfigDialog(int index) {
        showManualInputDialog(index);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (v2rayBroadCastReceiver != null) {
            unregisterReceiver(v2rayBroadCastReceiver);
        }
    }
}
