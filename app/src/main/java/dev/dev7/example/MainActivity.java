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
import android.widget.ScrollView;
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
    private TextView connection_speed, connection_traffic, connection_time, server_delay, connected_server_delay, connection_mode, btn_add_config, status_text;
    private EditText v2ray_config;
    private LinearLayout config_list_container;
    private SharedPreferences sharedPreferences;
    private BroadcastReceiver v2rayBroadCastReceiver;
    
    private ArrayList<ConfigItem> configList = new ArrayList<>();

    public static class ConfigItem {
        String name;
        String address;
        String port;
        String uuid;
        String flow;
        String encryption;
        String network;
        String headType;
        String sni;
        String content;

        public ConfigItem(String name, String address, String port, String uuid, String flow, String encryption, String network, String headType, String sni, String content) {
            this.name = name;
            this.address = address;
            this.port = port;
            this.uuid = uuid;
            this.flow = flow;
            this.encryption = encryption;
            this.network = network;
            this.headType = headType;
            this.sni = sni;
            this.content = content;
        }

        public void updateFullContent() {
            if (content != null && content.startsWith("vless://")) {
                // Reconstruct basic vless URI if edited manually
                this.content = "vless://" + uuid + "@" + address + ":" + port + "?encryption=" + encryption + "&security=" + (sni.isEmpty() ? "none" : "tls") + "&sni=" + sni + "&type=" + network + "#" + name;
            }
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
            btn_add_config = findViewById(R.id.btn_add_config);
            status_text = findViewById(R.id.status_text);
            config_list_container = findViewById(R.id.config_list_container);
        }

        sharedPreferences = getSharedPreferences("conf_prefs", MODE_PRIVATE);
        
        loadConfigsFromPrefs();
        refreshConfigListUI();

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

        // Connected server delay test (Only updates when tapped)
        connected_server_delay.setOnClickListener(view -> {
            connected_server_delay.setText("delay : measuring...");
            V2rayController.getConnectedV2rayServerDelay(this, delayResult -> runOnUiThread(() -> {
                if (delayResult >= 0) {
                    connected_server_delay.setText("delay : " + delayResult + "ms");
                } else {
                    connected_server_delay.setText("delay : timeout");
                }
            }));
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
                break;
            case DISCONNECTED:
                connection.setText("▶");
                status_text.setText("Disconnected");
                connected_server_delay.setText("delay : tap to test");
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
                configList.add(new ConfigItem(
                        obj.optString("name", "Config"),
                        obj.optString("address", ""),
                        obj.optString("port", "443"),
                        obj.optString("uuid", ""),
                        obj.optString("flow", "none"),
                        obj.optString("encryption", "none"),
                        obj.optString("network", "tcp"),
                        obj.optString("headType", "none"),
                        obj.optString("sni", ""),
                        obj.getString("content")
                ));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        
        String activeCfg = sharedPreferences.getString("active_config_content", "");
        v2ray_config.setText(activeCfg);
    }

    private void saveConfigsToPrefs() {
        JSONArray jsonArray = new JSONArray();
        try {
            for (ConfigItem item : configList) {
                JSONObject obj = new JSONObject();
                obj.put("name", item.name);
                obj.put("address", item.address);
                obj.put("port", item.port);
                obj.put("uuid", item.uuid);
                obj.put("flow", item.flow);
                obj.put("encryption", item.encryption);
                obj.put("network", item.network);
                obj.put("headType", item.headType);
                obj.put("sni", item.sni);
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

            TextView previewView = new TextView(this);
            previewView.setText(item.address.isEmpty() ? (item.content.length() > 40 ? item.content.substring(0, 40) + "..." : item.content) : (item.address + " : " + item.port));
            previewView.setTextColor(0xFFAAAAAA);
            previewView.setTextSize(11);
            previewView.setPadding(0, 6, 0, 0);
            card.addView(previewView);

            card.setOnClickListener(v -> {
                if (V2rayController.getConnectionState() == V2rayConstants.CONNECTION_STATES.CONNECTED) {
                    Toast.makeText(this, "please stop vpn connection before you select another config", Toast.LENGTH_LONG).show();
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
                    
                    // Simple parsing for vless/vmess
                    String address = "";
                    String port = "443";
                    String uuid = "";
                    String sni = "";
                    
                    try {
                        if (pastedConfig.startsWith("vless://") || pastedConfig.startsWith("vmess://")) {
                            int atIdx = pastedConfig.indexOf("@");
                            int colonIdx = pastedConfig.lastIndexOf(":");
                            int qIdx = pastedConfig.indexOf("?");
                            if (atIdx != -1 && colonIdx != -1) {
                                uuid = pastedConfig.substring(pastedConfig.indexOf("://") + 3, atIdx);
                                address = pastedConfig.substring(atIdx + 1, colonIdx);
                                if (qIdx != -1) {
                                    port = pastedConfig.substring(colonIdx + 1, qIdx);
                                } else {
                                    port = pastedConfig.substring(colonIdx + 1);
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    configList.add(new ConfigItem(configName, address, port, uuid, "none", "none", "tcp", "none", sni, pastedConfig));
                    saveConfigsToPrefs();
                    refreshConfigListUI();
                    
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
        builder.setTitle(isEdit ? "Configuration file (Edit)" : "Manual Config Input");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 16, 32, 16);

        ConfigItem existing = isEdit ? configList.get(editIndex) : null;

        final EditText nameInput = new EditText(this);
        nameInput.setHint("remarks (Name)");
        if (isEdit) nameInput.setText(existing.name);
        layout.addView(nameInput);

        final EditText addressInput = new EditText(this);
        addressInput.setHint("address");
        if (isEdit) addressInput.setText(existing.address);
        layout.addView(addressInput);

        final EditText portInput = new EditText(this);
        portInput.setHint("port");
        if (isEdit) portInput.setText(existing.port);
        layout.addView(portInput);

        final EditText uuidInput = new EditText(this);
        uuidInput.setHint("id (UUID)");
        if (isEdit) uuidInput.setText(existing.uuid);
        layout.addView(uuidInput);

        final EditText flowInput = new EditText(this);
        flowInput.setHint("flow");
        if (isEdit) flowInput.setText(existing.flow);
        layout.addView(flowInput);

        final EditText encryptionInput = new EditText(this);
        encryptionInput.setHint("encryption");
        if (isEdit) encryptionInput.setText(existing.encryption);
        layout.addView(encryptionInput);

        final EditText networkInput = new EditText(this);
        networkInput.setHint("network (e.g. tcp, ws)");
        if (isEdit) networkInput.setText(existing.network);
        layout.addView(networkInput);

        final EditText sniInput = new EditText(this);
        sniInput.setHint("SNI");
        if (isEdit) sniInput.setText(existing.sni);
        layout.addView(sniInput);

        final EditText rawContentInput = new EditText(this);
        rawContentInput.setHint("Full vmess:// or vless:// link / raw config");
        rawContentInput.setMinLines(3);
        if (isEdit) rawContentInput.setText(existing.content);
        layout.addView(rawContentInput);

        scrollView.addView(layout);
        builder.setView(scrollView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = nameInput.getText().toString().trim();
            String address = addressInput.getText().toString().trim();
            String port = portInput.getText().toString().trim();
            String uuid = uuidInput.getText().toString().trim();
            String flow = flowInput.getText().toString().trim();
            String encryption = encryptionInput.getText().toString().trim();
            String network = networkInput.getText().toString().trim();
            String sni = sniInput.getText().toString().trim();
            String rawContent = rawContentInput.getText().toString().trim();

            if (name.isEmpty()) name = "Config " + (configList.size() + 1);
            if (port.isEmpty()) port = "443";
            if (network.isEmpty()) network = "tcp";
            if (flow.isEmpty()) flow = "none";
            if (encryption.isEmpty()) encryption = "none";

            if (rawContent.isEmpty() && !address.isEmpty() && !uuid.isEmpty()) {
                rawContent = "vless://" + uuid + "@" + address + ":" + port + "?encryption=" + encryption + "&security=" + (sni.isEmpty() ? "none" : "tls") + "&sni=" + sni + "&type=" + network + "#" + name;
            }

            if (!rawContent.isEmpty()) {
                if (isEdit) {
                    ConfigItem item = configList.get(editIndex);
                    item.name = name;
                    item.address = address;
                    item.port = port;
                    item.uuid = uuid;
                    item.flow = flow;
                    item.encryption = encryption;
                    item.network = network;
                    item.sni = sni;
                    item.content = rawContent;
                    Toast.makeText(this, "Config Updated!", Toast.LENGTH_SHORT).show();
                } else {
                    configList.add(new ConfigItem(name, address, port, uuid, flow, encryption, network, "none", sni, rawContent));
                    if (configList.size() == 1) {
                        v2ray_config.setText(rawContent);
                        sharedPreferences.edit().putString("active_config_content", rawContent).apply();
                    }
                    Toast.makeText(this, "Config Added Successfully!", Toast.LENGTH_SHORT).show();
                }
                saveConfigsToPrefs();
                refreshConfigListUI();
                status_text.setText("Ready");
            } else {
                Toast.makeText(this, "Config content or address/id cannot be empty!", Toast.LENGTH_SHORT).show();
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
