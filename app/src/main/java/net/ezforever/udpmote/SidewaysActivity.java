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
import android.widget.ImageButton;
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

public class SidewaysActivity extends AppCompatActivity implements SensorEventListener, VGyroEventListener {
    private static final int STICK_DOWN = 5;
    private static final int STICK_DOWNLEFT = 6;
    private static final int STICK_DOWNRIGHT = 4;
    private static final int STICK_LEFT = 7;
    private static final int STICK_NONE = 0;
    private static final int STICK_RIGHT = 3;
    private static final int STICK_UP = 1;
    private static final int STICK_UPLEFT = 8;
    private static final int STICK_UPRIGHT = 2;
    private static final long maxDelayBetweenBackPresses = 500;
    private static final int totalBackPresses = 3;

    private static final byte PACKET_ACCEL = 1 << 0;
    private static final byte PACKET_BUTTONS = 1 << 1;
    private static final byte PACKET_IR = 1 << 2;
    //private static final byte PACKET_NUNCHUK = 1 << 3;
    //private static final byte PACKET_NUNCHUK_ACCEL = 1 << 4;

    private static final byte PACKET_ACCEL_TIMESTAMP = 1 << 5;
    private static final byte PACKET_GYRO = 1 << 6;

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
    Joystick joystickRight;
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
    TextView txtAngle2;
    TextView txtHold;
    TextView txtHold2;
    TextView txtOffset;
    TextView txtOffset2;
    TextView txtValue;
    /* access modifiers changed from: private */
    public DatagramSocket udpSocket;
    private PowerManager.WakeLock wl;

    private String serverAddress;
    private int serverPort;
    private int serverIndex;
    private int vGyroImpl;

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
        setContentView(R.layout.activity_sideways);
        this.backToast = Toast.makeText(getApplicationContext(), null, Toast.LENGTH_SHORT);
        this.wl = ((PowerManager) getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "UDPMote:sideways");
        this.irClickTimer = new Timer();

        Intent intent = getIntent();
        this.serverAddress = intent.getStringExtra("address");
        this.serverPort = intent.getIntExtra("port", 0);
        this.serverIndex = intent.getIntExtra("pID", 0);
        this.vGyroImpl = intent.getIntExtra("vGyroImpl", 0);

        this.sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // Every Android phone exists by now should have a accelerometer
        this.accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        // ... but that's not true for gyroscopes.
        this.vGyro = new VGyro(this, SensorManager.SENSOR_DELAY_GAME);
        this.vGyro.setImpl(VGyroImplInfo.values()[this.vGyroImpl]);

        GestureOverlayView gestureIR = (GestureOverlayView) findViewById(R.id.agesture_ir);
        //System.out.println(gestureIR.getMeasuredWidth() + "x" + gestureIR.getHeight());
        gestureIR.setOnTouchListener((v, event) -> {
            float ratio = 2.0f / ((float) Math.max(v.getWidth(), v.getHeight()));
            float x = event.getX() * ratio;
            float y = event.getY() * ratio;
            switch(event.getAction()) {
                case MotionEvent.ACTION_UP:
                    SidewaysActivity.this.irIsTouching = false;
                    SidewaysActivity.this.irClickUp = event.getEventTime();
                    if(SidewaysActivity.this.irClickUp - SidewaysActivity.this.irClickDown < 200
                    && Math.hypot(x - SidewaysActivity.this.irClickX, y - SidewaysActivity.this.irClickY) < 0.05f) {
                        SidewaysActivity.this.buttonMask.xor(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                        SidewaysActivity.this.irClickTask = new TimerTask() {
                            public void run() {
                                SidewaysActivity.this.buttonMask.xor(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                            }
                        };
                        SidewaysActivity.this.irClickTimer.schedule(SidewaysActivity.this.irClickTask, 75);
                    }
                    break;
                case MotionEvent.ACTION_DOWN:
                    SidewaysActivity.this.irIsTouching = true;
                    SidewaysActivity.this.irClickDown = event.getEventTime();
                    SidewaysActivity.this.irClickX = x;
                    SidewaysActivity.this.irClickY = y;
                    // Fall-through intended
                case MotionEvent.ACTION_MOVE:
                    if (event.getAction() == MotionEvent.ACTION_MOVE) {
                        SidewaysActivity.this.ir.add(x - SidewaysActivity.this.lastX, -(y - SidewaysActivity.this.lastY));
                    }
                    SidewaysActivity.this.lastX = x;
                    SidewaysActivity.this.lastY = y;
                    break;
                default:
            }
            return true;
        });
        this.maskMap.put(R.id.abutton_1, 1);
        this.maskMap.put(R.id.abutton_2, 2);
        this.maskMap.put(R.id.abutton_a, 4);
        this.maskMap.put(R.id.abutton_b, 8);
        this.maskMap.put(R.id.abutton_plus, 16);
        this.maskMap.put(R.id.abutton_minus, 32);
        this.maskMap.put(R.id.abutton_home, 64);
        this.maskMap.put(R.id.abutton_up, 128);
        this.maskMap.put(R.id.abutton_down, 256);
        this.maskMap.put(R.id.abutton_left, 512);
        this.maskMap.put(R.id.abutton_right, 1024);
        this.maskMap.put(R.id.abutton_sk, 2048);
        this.txtAngle = findViewById(R.id.txtAngle);
        this.txtOffset = findViewById(R.id.txtOffset);
        this.txtHold = findViewById(R.id.txtHold);
        this.txtAngle2 = findViewById(R.id.txtAngle2);
        this.txtOffset2 = findViewById(R.id.txtOffset2);
        this.txtHold2 = findViewById(R.id.txtHold2);
        this.txtValue = findViewById(R.id.txtValue);
        this.joystickLeft = findViewById(R.id.joystickLeft);
        this.joystickRight = findViewById(R.id.joystickRight);
        findViewById(R.id.tbSwitch2).setOnClickListener(v -> {
            SidewaysActivity.this.checkMode();
        });
        findViewById(R.id.abutton_middle).setOnClickListener(v -> {
            SidewaysActivity.this.Mode1();
        });
        for (Map.Entry<Integer, Integer> entry : this.maskMap.entrySet()) {
            findViewById(entry.getKey()).setOnTouchListener((v, event) -> {
                int action = event.getAction();
                if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP) {
                    SidewaysActivity.this.buttonMask.xor(SidewaysActivity.this.maskMap.get(v.getId()));
                    v.setPressed(action == MotionEvent.ACTION_DOWN);
                }
                return true;
            });
        }
        this.joystickLeft.setJoystickListener(new JoystickListener() {
            public void onDown() {
            }

            public void onDrag(float degrees, float offset) {
                SidewaysActivity.this.txtAngle.setText(String.valueOf(SidewaysActivity.this.angleConvert(degrees)));
                SidewaysActivity.this.txtOffset.setText(String.valueOf(SidewaysActivity.this.distanceConvert(offset)));
                int direction = SidewaysActivity.this.getDirection(degrees);
                if (direction == SidewaysActivity.STICK_UP) {
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_UPLEFT) {
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_LEFT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_DOWNLEFT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_DOWN) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_DOWNRIGHT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_RIGHT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                } else if (direction == SidewaysActivity.STICK_UPRIGHT) {
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_right));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                }
            }

            public void onUp() {
                SidewaysActivity.this.txtAngle.setText("");
                SidewaysActivity.this.txtOffset.setText("");
                SidewaysActivity.this.txtHold.setText("");
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_up));
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_left));
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_down));
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_right));
            }
        });
        this.joystickRight.setJoystickListener(new JoystickListener() {
            public void onDown() {
            }

            public void onDrag(float degrees, float offset) {
                /*
                    MOD:

                    Old mapping of joystick to buttons:
                        Up    -> 1
                        Left  -> 2
                        Right -> B
                        Down  -> A

                    New mapping of joystick to buttons:
                        Up    -> 2
                        Left  -> B
                        Right -> 1
                        Down  -> A
                */
                SidewaysActivity.this.txtAngle2.setText(String.valueOf(SidewaysActivity.this.angleConvert(degrees)));
                SidewaysActivity.this.txtOffset2.setText(String.valueOf(SidewaysActivity.this.distanceConvert(offset)));
                int direction = SidewaysActivity.this.getDirection4W(degrees);
                if (direction == SidewaysActivity.STICK_UP) {
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_UPLEFT) {
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_LEFT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_DOWNLEFT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_DOWN) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_DOWNRIGHT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_RIGHT) {
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                } else if (direction == SidewaysActivity.STICK_UPRIGHT) {
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                    SidewaysActivity.this.buttonMask.set  (SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                    SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
                }
            }

            public void onUp() {
                SidewaysActivity.this.txtAngle2.setText("");
                SidewaysActivity.this.txtOffset2.setText("");
                SidewaysActivity.this.txtHold2.setText("");
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_2));
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_b));
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_1));
                SidewaysActivity.this.buttonMask.clear(SidewaysActivity.this.maskMap.get(R.id.abutton_a));
            }
        });

        this.sendExecutor.schedule(() -> {
            try {
                SidewaysActivity.this.udpSocket = new DatagramSocket();
                SidewaysActivity.this.sendBuffer[0] = -34;

                try {
                    // This is for testing the hostname resolution
                    new DatagramPacket(SidewaysActivity.this.sendBuffer, SidewaysActivity.this.sendBuffer.length, InetAddress.getByName(serverAddress), serverPort);

                    //SidewaysActivity.this.scheduleSend();
                } catch (UnknownHostException e) {
                    SidewaysActivity.this.disconnect(SidewaysActivity.this.getResources().getString(R.string.resolv_failed));
                }
            } catch (SocketException e2) {
                SidewaysActivity.this.disconnect(SidewaysActivity.this.getResources().getString(R.string.socket_failed));
            }
        }, 0, TimeUnit.MILLISECONDS);
    }

    /* access modifiers changed from: private */
    public void Mode1() {
        Intent intent = new Intent(this, ControllerActivity.class);
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
            int mask = SidewaysActivity.this.buttonMask.get();
            long ts = SidewaysActivity.this.accelerometer.getTs();
            SidewaysActivity.this.accelerometer.get(SidewaysActivity.this.localAccelerometer);
            SidewaysActivity.this.gyroscope.get(SidewaysActivity.this.localGyroscope);
            SidewaysActivity.this.ir.get(SidewaysActivity.this.localIR);

            int offset = 1;
            SidewaysActivity.this.sendBuffer[offset + 1] =
                    PACKET_ACCEL | PACKET_BUTTONS | PACKET_ACCEL_TIMESTAMP | PACKET_GYRO;
            if (SidewaysActivity.this.irIsTouching)
                SidewaysActivity.this.sendBuffer[offset + 1] |= PACKET_IR;
            offset += 2;

            for (int i = 0; i < 3; i++) {
                // MOD: Originally there is a "boost multiplier" of 1.2 and g = 9.1
                int b = (int) ((SidewaysActivity.this.localAccelerometer[i] / SensorManager.GRAVITY_EARTH) * 1024.0f * 1024.0f);
                SidewaysActivity.this.sendBuffer[offset + 3] = (byte) (b & 255);
                SidewaysActivity.this.sendBuffer[offset + 2] = (byte) ((b >> 8) & 255);
                SidewaysActivity.this.sendBuffer[offset + 1] = (byte) ((b >> 16) & 255);
                SidewaysActivity.this.sendBuffer[offset] = (byte) ((b >> 24) & 255);
                offset += 4;
            }

            SidewaysActivity.this.sendBuffer[offset + 3] = (byte) (mask & 255);
            SidewaysActivity.this.sendBuffer[offset + 2] = (byte) ((mask >> 8) & 255);
            SidewaysActivity.this.sendBuffer[offset + 1] = (byte) ((mask >> 16) & 255);
            SidewaysActivity.this.sendBuffer[offset] = (byte) ((mask >> 24) & 255);
            offset += 4;

            if(SidewaysActivity.this.irIsTouching) {
                for (int i = 0; i < 2; i++) {
                    int b2 = (int) (SidewaysActivity.this.localIR[i] * 1024.0f * 1024.0f);
                    SidewaysActivity.this.sendBuffer[offset + 3] = (byte) (b2 & 255);
                    SidewaysActivity.this.sendBuffer[offset + 2] = (byte) ((b2 >> 8) & 255);
                    SidewaysActivity.this.sendBuffer[offset + 1] = (byte) ((b2 >> 16) & 255);
                    SidewaysActivity.this.sendBuffer[offset] = (byte) ((b2 >> 24) & 255);
                    offset += 4;
                }
            }

            for(int i = 7; i >= 0; i--) {
                SidewaysActivity.this.sendBuffer[offset + i] = (byte) ((ts >> ((7 - i) * 8)) & 255);
            }
            offset += 8;

            for (int i = 0; i < 3; i++) {
                // Don't know why 4/3 but it works perfectly
                int b = (int) ((SidewaysActivity.this.localGyroscope[i] * 4 / 3) * 1024.0f * 1024.0f);
                SidewaysActivity.this.sendBuffer[offset + 3] = (byte) (b & 255);
                SidewaysActivity.this.sendBuffer[offset + 2] = (byte) ((b >> 8) & 255);
                SidewaysActivity.this.sendBuffer[offset + 1] = (byte) ((b >> 16) & 255);
                SidewaysActivity.this.sendBuffer[offset] = (byte) ((b >> 24) & 255);
                offset += 4;
            }

            try {
                SidewaysActivity.this.udpSocket.send(new DatagramPacket(SidewaysActivity.this.sendBuffer, offset, InetAddress.getByName(SidewaysActivity.this.serverAddress), SidewaysActivity.this.serverPort));
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
            this.accelerometer.set(event.values);
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
/*
    public int getDirection8WOrig(float degrees) {
        float angle = (float) angleConvert(degrees);
        if (angle >= 85.0f && angle < 95.0f) {
            return STICK_UP;
        }
        if (angle >= 40.0f && angle < 50.0f) {
            return STICK_UPRIGHT;
        }
        if (angle >= 355.0f || angle < 5.0f) {
            return STICK_RIGHT;
        }
        if (angle >= 310.0f && angle < 320.0f) {
            return STICK_DOWNRIGHT;
        }
        if (angle >= 265.0f && angle < 275.0f) {
            return STICK_DOWN;
        }
        if (angle >= 220.0f && angle < 230.0f) {
            return STICK_DOWNLEFT;
        }
        if (angle >= 175.0f && angle < 185.0f) {
            return STICK_LEFT;
        }
        if (angle < 130.0f || angle >= 140.0f) {
            return STICK_NONE;
        }
        return STICK_UPLEFT;
    }
*/
    public void checkMode() {
        ImageButton bA = findViewById(R.id.abutton_a);
        ImageButton bB = findViewById(R.id.abutton_b);
        ImageButton b1 = findViewById(R.id.abutton_1);
        ImageButton b2 = findViewById(R.id.abutton_2);
        this.joystickRight = findViewById(R.id.joystickRight);
        if (((ToggleButton) findViewById(R.id.tbSwitch2)).isChecked()) {
            this.joystickRight.setVisibility(View.VISIBLE);
            this.joystickRight.setStartOnFirstTouch(true);
            bA.setVisibility(View.INVISIBLE);
            bB.setVisibility(View.INVISIBLE);
            b1.setVisibility(View.INVISIBLE);
            b2.setVisibility(View.INVISIBLE);
        } else {
            this.joystickRight.setVisibility(View.INVISIBLE);
            bA.setVisibility(View.VISIBLE);
            bB.setVisibility(View.VISIBLE);
            b1.setVisibility(View.VISIBLE);
            b2.setVisibility(View.VISIBLE);
        }
    }

    public int getDirection(float degrees) {
        float angle = (float) angleConvert(degrees);
        if (((ToggleButton) findViewById(R.id.tbSwitch)).isChecked()) {
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
        } else {
            if (angle >= 30.0f && angle < 60.0f) {
                return STICK_UPRIGHT;
            }
            if (angle >= 60.0f && angle < 120.0f) {
                return STICK_UP;
            }
            if (angle >= 120.0f && angle < 150.0f) {
                return STICK_UPLEFT;
            }
            if (angle >= 150.0f && angle < 210.0f) {
                return STICK_LEFT;
            }
            if (angle >= 210.0f && angle < 240.0f) {
                return STICK_DOWNLEFT;
            }
            if (angle >= 240.0f && angle < 300.0f) {
                return STICK_DOWN;
            }
            if (angle >= 300.0f && angle < 330.0f) {
                return STICK_DOWNRIGHT;
            }
            if (angle >= 330.0f || angle < 30.0f) {
                return STICK_RIGHT;
            }
        }
        return STICK_NONE;
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