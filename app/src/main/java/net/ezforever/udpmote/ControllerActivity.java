package net.ezforever.udpmote;

import androidx.appcompat.app.AppCompatActivity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.gesture.GestureOverlayView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.jmedeisis.bugstick.Joystick;
import com.jmedeisis.bugstick.JoystickListener;

import net.ezforever.vgyro.VGyro;
import net.ezforever.vgyro.VGyroEvent;
import net.ezforever.vgyro.VGyroEventListener;
import net.ezforever.vgyro.VGyroImplInfo;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ControllerActivity extends AppCompatActivity implements SensorEventListener, VGyroEventListener {
    private static final int STICK_DOWN = 5;
    private static final int STICK_DOWNLEFT = 6;
    private static final int STICK_DOWNRIGHT = 4;
    private static final int STICK_LEFT = 7;
    private static final int STICK_NONE = 0;
    private static final int STICK_RIGHT = 3;
    private static final int STICK_UP = 1;
    private static final int STICK_UPLEFT = 8;
    private static final int STICK_UPRIGHT = 2;

    private static final byte PACKET_ACCEL = 1 << 0;
    private static final byte PACKET_BUTTONS = 1 << 1;
    private static final byte PACKET_IR = 1 << 2;
    //private static final byte PACKET_NUNCHUK = 1 << 3;
    //private static final byte PACKET_NUNCHUK_ACCEL = 1 << 4;

    private static final byte PACKET_ACCEL_TIMESTAMP = 1 << 5;
    private static final byte PACKET_GYRO = 1 << 6;

    private static final long maxDelayBetweenBackPresses = 500;
    private static final int totalBackPresses = 3;
    /* access modifiers changed from: private */
    public final AtomicAccelerometerData accelerometer = new AtomicAccelerometerData();
    public final AtomicGyroscopeData gyroscope = new AtomicGyroscopeData();
    public SensorManager sensorManager;
    public Sensor accelerometerSensor;
    public VGyro vGyro;
    private int backPresses = 0;
    private Toast backToast;
    /* access modifiers changed from: private */
    public final AtomicButtonMask buttonMask = new AtomicButtonMask();
    /* access modifiers changed from: private */
    public final AtomicIRData ir = new AtomicIRData();
    Joystick joystickLeft;
    private long lastBackPress = 0;
    /* access modifiers changed from: private */
    public float lastX = 0.0f;
    /* access modifiers changed from: private */
    public float lastY = 0.0f;
    /* access modifiers changed from: private */
    public final float[] localAccelerometer = new float[3];
    public final float[] localGyroscope = new float[3];
    /* access modifiers changed from: private */
    public final float[] localIR = new float[2];
    /* access modifiers changed from: private */
    public final Map<Integer, Integer> maskMap = new HashMap<>();
    /* access modifiers changed from: private */
    public final byte[] sendBuffer = new byte[27 + 8 + 4 * 3];
    private final ScheduledExecutorService sendExecutor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> sendTask;
    /* access modifiers changed from: private */
    TextView txtAngle;
    TextView txtHold;
    TextView txtOffset;
    TextView txtValue;
    /* access modifiers changed from: private */
    public DatagramSocket udpSocket;
    private PowerManager.WakeLock wl;

    private String serverAddress;
    private int serverPort;
    private int serverIndex;
    private int vGyroImpl;

    private boolean controlsHidden = false;
    private final int[] controlsToHide = new int[] {
            R.id.gesture_ir, R.id.buttons, R.id.top1, R.id.top2, R.id.top3
    };

    private long irClickUp;
    private long irClickDown;
    private float irClickX;
    private float irClickY;
    private Timer irClickTimer;
    private TimerTask irClickTask;
    private boolean irIsTouching;

    /* access modifiers changed from: protected */
    @SuppressLint({"ShowToast"})
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_controller);

        this.backToast = Toast.makeText(getApplicationContext(), null, Toast.LENGTH_SHORT);
        this.wl = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "UDPMote:controller");
        this.irClickTimer = new Timer();

        Intent intent = getIntent();
        this.serverAddress = intent.getStringExtra("address");
        this.serverPort = intent.getIntExtra("port", 0);
        this.serverIndex = intent.getIntExtra("pID", 0);
        this.vGyroImpl = intent.getIntExtra("vGyroImpl", 0);
        //ledLight("Player " + intent.getIntExtra("pID", 0));
        ledLight(this.serverIndex);

        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Every Android phone exists by now should have a accelerometer
        this.accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // ... but that's not true for gyroscopes.
        this.vGyro = new VGyro(this, SensorManager.SENSOR_DELAY_GAME);
        this.vGyro.setImpl(VGyroImplInfo.values()[this.vGyroImpl]);

        GestureOverlayView gestureIR = (GestureOverlayView) findViewById(R.id.gesture_ir);
        //System.out.println(gestureIR.getMeasuredWidth() + "x" + gestureIR.getHeight());
        gestureIR.setOnTouchListener((v, event) -> {
            float ratio = 2.0f / ((float) Math.max(v.getWidth(), v.getHeight()));
            float x = event.getX() * ratio;
            float y = event.getY() * ratio;
            switch(event.getAction()) {
                case MotionEvent.ACTION_UP:
                    ControllerActivity.this.irIsTouching = false;
                    ControllerActivity.this.irClickUp = event.getEventTime();
                    if(ControllerActivity.this.irClickUp - ControllerActivity.this.irClickDown < 200
                    && Math.hypot(x - ControllerActivity.this.irClickX, y - ControllerActivity.this.irClickY) < 0.05f) {
                        ControllerActivity.this.buttonMask.xor(ControllerActivity.this.maskMap.get(R.id.button_a));
                        ControllerActivity.this.irClickTask = new TimerTask() {
                            public void run() {
                                ControllerActivity.this.buttonMask.xor(ControllerActivity.this.maskMap.get(R.id.button_a));
                            }
                        };
                        ControllerActivity.this.irClickTimer.schedule(ControllerActivity.this.irClickTask, 75);
                    }
                    break;
                case MotionEvent.ACTION_DOWN:
                    ControllerActivity.this.irIsTouching = true;
                    ControllerActivity.this.irClickDown = event.getEventTime();
                    ControllerActivity.this.irClickX = x;
                    ControllerActivity.this.irClickY = y;
                    // Fall-through intended
                case MotionEvent.ACTION_MOVE:
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        ControllerActivity.this.ir.add(x - ControllerActivity.this.lastX, -(y - ControllerActivity.this.lastY));
                    }
                    ControllerActivity.this.lastX = x;
                    ControllerActivity.this.lastY = y;
                    break;
                default:
            }
            return true;
        });
        this.maskMap.put(R.id.button_1, 1);
        this.maskMap.put(R.id.button_2, 2);
        this.maskMap.put(R.id.button_a, 4);
        this.maskMap.put(R.id.button_b, 8);
        this.maskMap.put(R.id.button_plus, 16);
        this.maskMap.put(R.id.button_minus, 32);
        this.maskMap.put(R.id.button_home, 64);
        this.maskMap.put(R.id.button_up, 128);
        this.maskMap.put(R.id.button_down, 256);
        this.maskMap.put(R.id.button_left, 512);
        this.maskMap.put(R.id.button_right, 1024);
        this.maskMap.put(R.id.button_ul, 640);
        this.maskMap.put(R.id.button_ur, 1152);
        this.maskMap.put(R.id.button_dl, 768);
        this.maskMap.put(R.id.button_dr, 1280);
        this.txtAngle = findViewById(R.id.txtAngle);
        this.txtOffset = findViewById(R.id.txtOffset);
        this.txtHold = findViewById(R.id.txtHold);
        this.txtValue = findViewById(R.id.txtValue);
        this.joystickLeft = findViewById(R.id.joystickLeft);
        findViewById(R.id.button_middle).setOnClickListener(v -> {
            ControllerActivity.this.Mode2();
        });
        findViewById(R.id.leds).setOnClickListener(view -> {
            ControllerActivity.this.controlsHidden = !ControllerActivity.this.controlsHidden;
            int state = ControllerActivity.this.controlsHidden ? View.INVISIBLE : View.VISIBLE;
            for(int id : controlsToHide)
                findViewById(id).setVisibility(state);
        });
        for (Map.Entry<Integer, Integer> entry : this.maskMap.entrySet()) {
            ((ImageButton) findViewById(entry.getKey().intValue())).setOnTouchListener((View.OnTouchListener) (v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                    ControllerActivity.this.buttonMask.xor(ControllerActivity.this.maskMap.get(v.getId()));
                    v.setPressed(action == MotionEvent.ACTION_DOWN);
                }
                return true;
            });
        }
        this.joystickLeft.setJoystickListener(new JoystickListener() {
            public void onDown() {
            }

            public void onDrag(float degrees, float offset) {
                ControllerActivity.this.txtAngle.setText(String.valueOf(ControllerActivity.this.angleConvert(degrees)));
                ControllerActivity.this.txtOffset.setText(String.valueOf(ControllerActivity.this.distanceConvert(offset)));
                ToggleButton toggleButton = ControllerActivity.this.findViewById(R.id.tbSwitch);
                int direction = ControllerActivity.this.getDirection4W(degrees);
                if (direction == ControllerActivity.STICK_UP) {
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_UPLEFT) {
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_LEFT) {
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_DOWNLEFT) {
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_DOWN) {
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_DOWNRIGHT) {
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_RIGHT) {
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_down));
                } else if (direction == ControllerActivity.STICK_UPRIGHT) {
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_up));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_left));
                    ControllerActivity.this.buttonMask.set  (ControllerActivity.this.maskMap.get(R.id.button_right));
                    ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_down));
                }
            }

            public void onUp() {
                ControllerActivity.this.txtAngle.setText("");
                ControllerActivity.this.txtOffset.setText("");
                ControllerActivity.this.txtHold.setText("");
                ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_up));
                ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_left));
                ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_down));
                ControllerActivity.this.buttonMask.clear(ControllerActivity.this.maskMap.get(R.id.button_right));
            }
        });

        this.sendExecutor.schedule(new Runnable() {
            public void run() {
                try {
                    ControllerActivity.this.udpSocket = new DatagramSocket();
                    ControllerActivity.this.sendBuffer[0] = -34;

                    try {
                        // This is for testing the hostname resolution
                        new DatagramPacket(ControllerActivity.this.sendBuffer, ControllerActivity.this.sendBuffer.length, InetAddress.getByName(serverAddress), serverPort);

                        //ControllerActivity.this.scheduleSend();
                    } catch (UnknownHostException e) {
                        ControllerActivity.this.disconnect(ControllerActivity.this.getResources().getString(R.string.resolv_failed));
                    }
                } catch (SocketException e2) {
                    ControllerActivity.this.disconnect(ControllerActivity.this.getResources().getString(R.string.socket_failed));
                }
            }
        }, 0, TimeUnit.MILLISECONDS);
    }
/*
    public static int extractNumberFromAnyAlphaNumeric(String alphaNumeric) {
        String alphaNumeric2 = alphaNumeric.length() > 0 ? alphaNumeric.replaceAll("\\D+", "") : "";
        return alphaNumeric2.length() > 0 ? Integer.parseInt(alphaNumeric2) : 0;
    }
*/
    public void ledLight(int ledP) {
        //int ledP = extractNumberFromAnyAlphaNumeric(led);
        ImageView p1Led = findViewById(R.id.p1led);
        ImageView p2Led = findViewById(R.id.p2led);
        ImageView p3Led = findViewById(R.id.p3led);
        ImageView p4Led = findViewById(R.id.p4led);
        p1Led.setImageResource(R.drawable.ledoff);
        p2Led.setImageResource(R.drawable.ledoff);
        p3Led.setImageResource(R.drawable.ledoff);
        p4Led.setImageResource(R.drawable.ledoff);
        switch (ledP) {
            case 1:
                p1Led.setImageResource(R.drawable.ledon);
                return;
            case 2:
                p4Led.setImageResource(R.drawable.ledon);
                return;
            case 3:
                p3Led.setImageResource(R.drawable.ledon);
                return;
            case 4:
                p2Led.setImageResource(R.drawable.ledon);
                return;
            default:
        }
    }

    /* access modifiers changed from: private */
    public void Mode2() {
        Intent intent = new Intent(this, SidewaysActivity.class);
        intent.putExtra("address", this.serverAddress);
        intent.putExtra("port", this.serverPort);
        intent.putExtra("pID", this.serverIndex);
        intent.putExtra("vGyroImpl", this.vGyroImpl);
        this.startActivity(intent);
        this.finish();
    }

    /* access modifiers changed from: private */
    public void scheduleSend() {
        if(this.sendTask != null)
            return;

        this.sendTask = this.sendExecutor.scheduleAtFixedRate(() -> {
            int mask = ControllerActivity.this.buttonMask.get();
            long ts = ControllerActivity.this.accelerometer.getTs();
            if (((CheckBox) ControllerActivity.this.findViewById(R.id.cbJDanceFix)).isChecked()) {
                ControllerActivity.this.accelerometer.swap_get(ControllerActivity.this.localAccelerometer);
            } else {
                ControllerActivity.this.accelerometer.get(ControllerActivity.this.localAccelerometer);
            }
            ControllerActivity.this.gyroscope.get(ControllerActivity.this.localGyroscope);
            ControllerActivity.this.ir.get(ControllerActivity.this.localIR);

            float boostMultiplier = 1.0f;
            RadioButton rb1 = ControllerActivity.this.findViewById(R.id.rb1);
            RadioButton rb2 = ControllerActivity.this.findViewById(R.id.rb2);
            RadioButton rb3 = ControllerActivity.this.findViewById(R.id.rb3);
            RadioButton rb4 = ControllerActivity.this.findViewById(R.id.rb4);
            if (rb1.isChecked()) {
                boostMultiplier = 1.0f; // MOD: was 1.12f
            } else if (rb2.isChecked()) {
                boostMultiplier = 1.1f; // MOD: was 1.17f
            } else if (rb3.isChecked()) {
                boostMultiplier = 1.2f; // MOD: was 1.22f
            } else if (rb4.isChecked()) {
                boostMultiplier = 1.3f; // MOD: was 1.27f
            }

            int offset = 1;

            ControllerActivity.this.sendBuffer[offset + 1] =
                PACKET_ACCEL | PACKET_BUTTONS | PACKET_ACCEL_TIMESTAMP | PACKET_GYRO;
            if (ControllerActivity.this.irIsTouching)
                ControllerActivity.this.sendBuffer[offset + 1] |= PACKET_IR;
            offset += 2;

            for (int i = 0; i < 3; i++) {
                //int b = (int) ((((ControllerActivity.this.localAccelerometer[i] * boostMultiplier) * 1024.0f) * 1024.0f) / (9.1f * boostMultiplier));
                int b = (int) ((ControllerActivity.this.localAccelerometer[i] * boostMultiplier / SensorManager.GRAVITY_EARTH) * 1024.0f * 1024.0f);
                ControllerActivity.this.sendBuffer[offset + 3] = (byte) (b & 255);
                ControllerActivity.this.sendBuffer[offset + 2] = (byte) ((b >> 8) & 255);
                ControllerActivity.this.sendBuffer[offset + 1] = (byte) ((b >> 16) & 255);
                ControllerActivity.this.sendBuffer[offset] = (byte) ((b >> 24) & 255);
                offset += 4;
            }

            ControllerActivity.this.sendBuffer[offset + 3] = (byte) (mask & 255);
            ControllerActivity.this.sendBuffer[offset + 2] = (byte) ((mask >> 8) & 255);
            ControllerActivity.this.sendBuffer[offset + 1] = (byte) ((mask >> 16) & 255);
            ControllerActivity.this.sendBuffer[offset] = (byte) ((mask >> 24) & 255);
            offset += 4;

            if(ControllerActivity.this.irIsTouching) {
                for (int i = 0; i < 2; i++) {
                    int b2 = (int) (ControllerActivity.this.localIR[i] * 1024.0f * 1024.0f);
                    ControllerActivity.this.sendBuffer[offset + 3] = (byte) (b2 & 255);
                    ControllerActivity.this.sendBuffer[offset + 2] = (byte) ((b2 >> 8) & 255);
                    ControllerActivity.this.sendBuffer[offset + 1] = (byte) ((b2 >> 16) & 255);
                    ControllerActivity.this.sendBuffer[offset] = (byte) ((b2 >> 24) & 255);
                    offset += 4;
                }
            }

            for(int i = 7; i >= 0; i--) {
                ControllerActivity.this.sendBuffer[offset + i] = (byte) ((ts >> ((7 - i) * 8)) & 255);
            }
            offset += 8;

            for (int i = 0; i < 3; i++) {
                // Don't know why 4/3 but it works perfectly
                int b = (int) ((ControllerActivity.this.localGyroscope[i] * 4 / 3) * 1024.0f * 1024.0f);
                ControllerActivity.this.sendBuffer[offset + 3] = (byte) (b & 255);
                ControllerActivity.this.sendBuffer[offset + 2] = (byte) ((b >> 8) & 255);
                ControllerActivity.this.sendBuffer[offset + 1] = (byte) ((b >> 16) & 255);
                ControllerActivity.this.sendBuffer[offset] = (byte) ((b >> 24) & 255);
                offset += 4;
            }

            try {
                ControllerActivity.this.udpSocket.send(new DatagramPacket(ControllerActivity.this.sendBuffer, offset, InetAddress.getByName(ControllerActivity.this.serverAddress), ControllerActivity.this.serverPort));
            } catch (IOException ignored) {
            }
        }, 33, 33, TimeUnit.MILLISECONDS);
    }

    public void scheduleCancel() {
        if(this.sendTask != null) {
            this.sendTask.cancel(false);
            this.sendTask = null;
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void onSensorChanged(SensorEvent event) {
        // Wait for sensors to stabilize
        //if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
            //return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            this.accelerometer.setTs(event.timestamp);

            if (((CheckBox) findViewById(R.id.cbJDanceFix)).isChecked()) {
                this.accelerometer.swap_set(event.values);
            } else {
                this.accelerometer.set(event.values);
            }
        }
    }

    public void onVGyroChanged(VGyroEvent event) {
        this.gyroscope.set(event.values);
    }

    /* access modifiers changed from: protected */
    public void onResume() {
        this.wl.acquire();
        sensorManager.registerListener(this, this.accelerometerSensor, SensorManager.SENSOR_DELAY_GAME);
        this.vGyro.registerListener(this);
        this.scheduleSend();
        super.onResume();
    }

    /* access modifiers changed from: protected */
    public void onPause() {
        this.wl.release();
        this.scheduleCancel();
        sensorManager.unregisterListener(this, this.accelerometerSensor);
        this.vGyro.unregisterListener(this);
        super.onPause();
    }

    /* access modifiers changed from: protected */
    public void onDestroy() {
        this.sendExecutor.shutdown();
        try {
            this.sendExecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.udpSocket.close();
        super.onDestroy();
    }

    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_BACK) {
            return super.onKeyDown(keyCode, event);
        }
        long currentTime = System.currentTimeMillis();
        if (currentTime - this.lastBackPress > maxDelayBetweenBackPresses) {
            this.backPresses = 1;
        } else {
            this.backPresses++;
        }
        this.lastBackPress = currentTime;
        if (this.backPresses >= totalBackPresses) {
            this.backToast.cancel();
            disconnect(null);
            return true;
        }
        this.backToast.setText((totalBackPresses - this.backPresses) + getResources().getString(R.string.presses_to_go));
        this.backToast.show();
        return true;
    }

    /* access modifiers changed from: private */
    public void disconnect(String errorMessage) {
        Intent intent = new Intent(this, ConnectionActivity.class);
        if (errorMessage != null) {
            intent.putExtra("error", errorMessage);
        }
        startActivity(intent);
        finish();
    }

    public int getDirection4W(float degrees) {
        float angle = (float) angleConvert(degrees);
        if (angle >= 45.0f && angle < 135.0f) {
            return STICK_UP;
        }
        if (angle >= 135.0f && angle < 225.0f) {
            return STICK_LEFT;
        }
        if (angle >= 225.0f && angle < 315.0f) {
            return STICK_DOWN;
        }
        if (angle >= 315.0f || angle < 45.0f) {
            return STICK_RIGHT;
        }
        return STICK_NONE;
    }

    public int angleConvert(float degrees) {
        if (((int) degrees) < 0) {
            return ((int) degrees) + 360;
        }
        return (int) degrees;
    }

    public int distanceConvert(float offset) {
        return (int) (100.0f * offset);
    }
}