package net.ezforever.udpmote;

import androidx.appcompat.app.AppCompatActivity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import net.ezforever.vgyro.VGyroImplSelectorActivity;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ConnectionActivity extends AppCompatActivity {
    public static final int REQ_VGYRO_IMPL = 0x3000;

    // ---

    private final byte[] buffer = new byte[512];
    /* access modifiers changed from: private */
    public final ActiveServersList activeServers = new ActiveServersList();
    /* access modifiers changed from: private */
    public final DatagramPacket broadcastPacket = new DatagramPacket(this.buffer, this.buffer.length);
    /* access modifiers changed from: private */
    public DatagramSocket broadcastSocket;
    /* access modifiers changed from: private */
    public final Map<Integer, UdpwiiServer> localActiveServersList = new TreeMap<>();
    private final ScheduledExecutorService maintenanceExecutor = Executors.newSingleThreadScheduledExecutor();
    /* access modifiers changed from: private */
    public RadioGroup serverGroup;
    private SharedPreferences settings;
    private SharedPreferences settingsVGyro;

    private int vGyroImpl;

    private void showVGyroImplChooser() {
        this.startActivityForResult(new Intent(this, VGyroImplSelectorActivity.class), REQ_VGYRO_IMPL);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQ_VGYRO_IMPL) {
            // User might cancel the selector activity but it's fine
            // The selector's onCreate() will initialize the default sensor value and send it to settings
            this.vGyroImpl = this.settingsVGyro.getInt(this.getString(R.string.vgyro_pref_impl_key), 0);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /* access modifiers changed from: protected */
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        this.settings = this.getPreferences(Context.MODE_PRIVATE);
        this.serverGroup = findViewById(R.id.group_server);
        Button connectButton = findViewById(R.id.button_connect);
        Button aboutButton = findViewById(R.id.button_about);
        Button customButton = findViewById(R.id.button_custom);
        ImageButton gyroscopeButton = findViewById(R.id.button_gyroscope);

        AlertDialog.Builder aboutDialogBuilder = new AlertDialog.Builder(this);
        aboutDialogBuilder.setMessage(R.string.about_content);
        aboutDialogBuilder.setTitle(R.string.about);
        aboutDialogBuilder.setPositiveButton(android.R.string.ok, null);
        final AlertDialog aboutDialog = aboutDialogBuilder.create();

        View customConnectionView = getLayoutInflater().inflate(R.layout.custom_connection, null);
        final EditText customServerAddress = customConnectionView.findViewById(R.id.server_address);
        final EditText customServerPort = customConnectionView.findViewById(R.id.server_port);
        customServerAddress.setText(this.settings.getString("customServerAddress", ""));
        int storedCustomPort = this.settings.getInt("customServerPort", 0);
        customServerPort.setText(storedCustomPort > 0 ? Integer.toString(storedCustomPort) : "");
        AlertDialog.Builder customDialogBuilder = new AlertDialog.Builder(this);
        customDialogBuilder.setTitle(R.string.custom_connection);
        customDialogBuilder.setNegativeButton(android.R.string.cancel, null);
        customDialogBuilder.setPositiveButton(R.string.connect, (dialog, which) -> {
            if (customServerAddress.getText().length() > 0 && customServerPort.getText().length() > 0) {
                ConnectionActivity.this.switchToController(customServerAddress.getText().toString(), Integer.parseInt(customServerPort.getText().toString()), true, 0);
            }
        }).setView(customConnectionView);
        final AlertDialog customDialog = customDialogBuilder.create();

        aboutButton.setOnClickListener(v -> {
            aboutDialog.show();
            ((TextView) aboutDialog.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());
        });

        customButton.setOnClickListener(v -> {
            customDialog.show();
        });

        connectButton.setOnClickListener(v -> {
            int chosenID = ConnectionActivity.this.serverGroup.getCheckedRadioButtonId();
            if (chosenID != -1) {
                UdpwiiServer chosenServer = ConnectionActivity.this.localActiveServersList.get(chosenID);
                ConnectionActivity.this.switchToController(chosenServer.address, chosenServer.port, false, chosenServer.index);
            }
        });

        gyroscopeButton.setOnClickListener(view -> {
            ConnectionActivity.this.showVGyroImplChooser();
        });

        String error = getIntent().getStringExtra("error");
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
        }

        this.settingsVGyro = this.getSharedPreferences(this.getString(R.string.vgyro_pref_name), Context.MODE_PRIVATE);
        this.vGyroImpl = this.settingsVGyro.getInt("vGyroImpl", -1);
        if(this.vGyroImpl < 0) {
            AlertDialog.Builder gyroDialogBuilder = new AlertDialog.Builder(this);
            gyroDialogBuilder.setMessage(R.string.gyro_dialog_content);
            gyroDialogBuilder.setTitle(R.string.gyro_dialog);
            gyroDialogBuilder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
                ConnectionActivity.this.showVGyroImplChooser();
            });
            gyroDialogBuilder.create().show();
        }

        startReceivingBroadcasts();
    }

    /* access modifiers changed from: protected */
    public void onDestroy() {
        stopReceivingBroadcasts();
        super.onDestroy();
    }

    private void startReceivingBroadcasts() {
        try {
            this.broadcastSocket = new DatagramSocket(4431);
        } catch (SocketException e) {
            e.printStackTrace();
        }
        try {
            this.broadcastSocket.setSoTimeout(1);
        } catch (SocketException e2) {
            e2.printStackTrace();
        }
        this.maintenanceExecutor.scheduleAtFixedRate(() -> {
            ConnectionActivity.this.activeServers.cleanup();
            while (true) {
                try {
                    ConnectionActivity.this.broadcastSocket.receive(ConnectionActivity.this.broadcastPacket);
                    if (UdpwiiServer.validatePacket(ConnectionActivity.this.broadcastPacket)) {
                        ConnectionActivity.this.activeServers.addServer(new UdpwiiServer(ConnectionActivity.this.broadcastPacket));
                    }
                } catch (SocketTimeoutException e) {
                    ConnectionActivity.this.runOnUiThread(() -> {
                        if (ConnectionActivity.this.activeServers.getServerListIfChanged(ConnectionActivity.this.localActiveServersList)) {
                            int checkedId = ConnectionActivity.this.serverGroup.getCheckedRadioButtonId();
                            boolean removed = true;
                            ConnectionActivity.this.serverGroup.removeAllViews();
                            for (Map.Entry<Integer, UdpwiiServer> entry : ConnectionActivity.this.localActiveServersList.entrySet()) {
                                RadioButton serverRadio = new RadioButton(ConnectionActivity.this);
                                //serverRadio.setText(entry.getValue().name + " " + entry.getValue().index + "\n[" + entry.getValue().address + ":" + entry.getValue().port + "]");
                                UdpwiiServer value = entry.getValue();
                                serverRadio.setText(String.format("%s %s\n[%s:%d]", value.name, value.index, value.address, value.port));
                                serverRadio.setId(value.id);
                                ConnectionActivity.this.serverGroup.addView(serverRadio);
                                if (value.id == checkedId) {
                                    removed = false;
                                }
                            }
                            ConnectionActivity.this.serverGroup.clearCheck();
                            if (!removed) {
                                ConnectionActivity.this.serverGroup.check(checkedId);
                            }
                        }
                    });
                    return;
                } catch (IOException e2) {
                    e2.printStackTrace();
                    ConnectionActivity.this.runOnUiThread(() -> {
                        if (ConnectionActivity.this.activeServers.getServerListIfChanged(ConnectionActivity.this.localActiveServersList)) {
                            int checkedId = ConnectionActivity.this.serverGroup.getCheckedRadioButtonId();
                            boolean removed = true;
                            ConnectionActivity.this.serverGroup.removeAllViews();
                            for (Map.Entry<Integer, UdpwiiServer> entry : ConnectionActivity.this.localActiveServersList.entrySet()) {
                                RadioButton serverRadio = new RadioButton(ConnectionActivity.this);
                                //serverRadio.setText(entry.getValue().name + " " + entry.getValue().index + "\n[" + entry.getValue().address + ":" + entry.getValue().port + "]");
                                UdpwiiServer value = entry.getValue();
                                serverRadio.setText(String.format("%s %s\n[%s:%d]", value.name, value.index, value.address, value.port));
                                serverRadio.setId(value.id);
                                ConnectionActivity.this.serverGroup.addView(serverRadio);
                                if (value.id == checkedId) {
                                    removed = false;
                                }
                            }
                            ConnectionActivity.this.serverGroup.clearCheck();
                            if (!removed) {
                                ConnectionActivity.this.serverGroup.check(checkedId);
                            }
                        }
                    });
                    return;
                }
            }
        }, 200, 200, TimeUnit.MILLISECONDS);
    }

    private void stopReceivingBroadcasts() {
        this.maintenanceExecutor.shutdown();
        try {
            this.maintenanceExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.broadcastSocket.close();
    }

    /* access modifiers changed from: private */
    public void switchToController(String address, int port, boolean custom, int index) {
        if (custom) {
            SharedPreferences.Editor editor = this.settings.edit();
            editor.putString("customServerAddress", address);
            editor.putInt("customServerPort", port);
            editor.apply();
        }
        stopReceivingBroadcasts();
        Intent controllerIntent = new Intent(this, ControllerActivity.class);
        controllerIntent.putExtra("address", address);
        controllerIntent.putExtra("port", port);
        controllerIntent.putExtra("pID", index);
        controllerIntent.putExtra("vGyroImpl", this.vGyroImpl);
        startActivity(controllerIntent);
        finish();
    }

    /*
    public static int extractNumberFromAnyAlphaNumeric(String alphaNumeric) {
        String alphaNumeric2 = alphaNumeric.length() > 0 ? alphaNumeric.replaceAll("\\D+", "") : "";
        if (alphaNumeric2.length() > 0) {
            return Integer.parseInt(alphaNumeric2);
        }
        return 0;
    }

    public int testFind() {
        int z = 0;
        for (int x = 0; x < this.serverGroup.getChildCount(); x++) {
            View v = this.serverGroup.getChildAt(x);
            if ((v instanceof RadioButton) && ((RadioButton) v).isChecked()) {
                z = extractNumberFromAnyAlphaNumeric((" " + ((RadioButton) v).getText()).substring(0, 14));
            }
        }
        return z;
    }
    */
}