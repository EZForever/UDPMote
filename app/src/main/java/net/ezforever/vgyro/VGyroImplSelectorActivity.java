package net.ezforever.vgyro;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Spinner;

import net.ezforever.udpmote.R;

import java.util.ArrayList;
import java.util.List;

public class VGyroImplSelectorActivity extends AppCompatActivity implements VGyroEventListener {

    private interface FibonacciSphereCallback {
        void callback(Vec3 point);
    }

    private static class Vec3 {

        public float x, y, z;

        public Vec3() {
            this.x = this.y = this.z = 0f;
        }

        public Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public float getMagnitude() {
            return (float) Math.sqrt(this.x * this.x + this.y * this.y + this.z * this.z);
        }

        public Vec3 normalize() {
            float magnitude = this.getMagnitude();
            if(magnitude == 0f)
                return new Vec3(); // Zero vector is not normalizable
            else
                return new Vec3(this.x / magnitude, this.y / magnitude, this.z / magnitude);
        }

        public Vec3 transform(float[] matrix33) {
            Vec3 vec = new Vec3();
            vec.x = matrix33[0] * this.x + matrix33[1] * this.y + matrix33[2] * this.z;
            vec.y = matrix33[3] * this.x + matrix33[4] * this.y + matrix33[5] * this.z;
            vec.z = matrix33[6] * this.x + matrix33[7] * this.y + matrix33[8] * this.z;
            return vec;
        }

    }
    
    // ---

    private static final float NS2S = 1e-9f; //1.0f / 1000000000.0f;
    private static final float GOLDEN_ANGLE = (float) (Math.PI * (3 - Math.sqrt(5)));
    private static final float[] MATRIX33_IDENTITY = new float[] {
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };

    private static float[] matrix33Transpose(float[] matrix33) {
        return new float[] {
                matrix33[0], matrix33[3], matrix33[6],
                matrix33[1], matrix33[4], matrix33[7],
                matrix33[2], matrix33[5], matrix33[8]
        };
    }

    private static float[] matrix33Multiply(float[] lhs, float[] rhs) {
        Vec3 col1 = new Vec3(rhs[0], rhs[3], rhs[6]).transform(lhs);
        Vec3 col2 = new Vec3(rhs[1], rhs[4], rhs[7]).transform(lhs);
        Vec3 col3 = new Vec3(rhs[2], rhs[5], rhs[8]).transform(lhs);
        return new float[] {
                col1.x, col2.x, col3.x,
                col1.y, col2.y, col3.y,
                col1.z, col2.z, col3.z
        };
    }

    // ---

    private ImageView imgGyro;
    private Spinner spnImpls;
    private SharedPreferences settings;

    private Bitmap imgGyro_bitmap;
    private Canvas imgGyro_canvas;
    private Paint pen_default;
    private Paint pen_point1;
    private Paint pen_point2;
    private Vec3 sphereDims;


    private VGyro vGyro;
    private List<VGyroImplInfo> availableImpls;

    private long prevTimestamp;
    private float[] prevMatrix;

    private void getFibonacciSphere(int pointCount, FibonacciSphereCallback callback) {
        for(int i = 0; i < pointCount; i++) {
            float z = (1f / pointCount - 1) + (2f / pointCount) * i;
            float r = (float) Math.sqrt(1.f - z * z);
            float x = (float) (Math.cos(GOLDEN_ANGLE * i) * r);
            float y = (float) (Math.sin(GOLDEN_ANGLE * i) * r);

            callback.callback(new Vec3(x, y, z));
        }
    }

    private void drawPoint(float x, float z, Paint pen) {
        float dx = this.sphereDims.x + x * this.sphereDims.y;
        float dz = this.sphereDims.z + z * this.sphereDims.y;
        this.imgGyro_canvas.drawPoint(dx, dz, pen);
    }

    private void drawGyro(float[] matrix) {
        // Clear canvas first
        this.imgGyro_canvas.drawColor(Color.WHITE);

        // The outline
        this.imgGyro_canvas.drawCircle(this.sphereDims.x, this.sphereDims.z, this.sphereDims.y, this.pen_default);

        // Dots on the sphere
        this.getFibonacciSphere(200, (Vec3 point) -> {
            Vec3 pointNew = point.transform(matrix);
            if(pointNew.y > 0)
                this.drawPoint(pointNew.x, pointNew.z, this.pen_default);
        });

        // Red dot on the top
        Vec3 point1 = new Vec3(0, 0, -1.25f).transform(matrix);
        if(point1.y > 0 || Math.hypot(point1.x, point1.z) > 1f)
            this.drawPoint(point1.x, point1.z, this.pen_point1);

        // Blue dot on the front
        Vec3 point2 = new Vec3(0, 1.25f, 0).transform(matrix);
        if(point2.y > 0 || Math.hypot(point2.x, point2.z) > 1f)
            this.drawPoint(point2.x, point2.z, this.pen_point2);

        // Force view to update
        this.imgGyro.postInvalidate();
    }

    private void resetGyro() {
        this.prevMatrix = MATRIX33_IDENTITY.clone();
        this.drawGyro(MATRIX33_IDENTITY);
    }

    private void setImpl(int idx, boolean save) {
        VGyroImplInfo info = this.availableImpls.get(idx);
        this.vGyro.setImpl(info);
        if(save) {
            this.settings.edit().putInt(this.getString(R.string.vgyro_pref_impl_key), info.ordinal()).apply();
        }
        this.resetGyro();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vgyro_impl_selector);
        setTitle(getString(R.string.vgyro_impl_selector_title));

        // Widgets for convenience
        this.imgGyro = this.findViewById(R.id.vGyro_imgGyro);
        this.spnImpls = this.findViewById(R.id.vGyro_spnImpls);
        this.settings = this.getSharedPreferences(this.getString(R.string.vgyro_pref_name), Context.MODE_PRIVATE);

        // Initialize ImageView's canvas
        ViewGroup.LayoutParams imgGyro_dims = this.imgGyro.getLayoutParams();
        this.imgGyro_bitmap = Bitmap.createBitmap(imgGyro_dims.width, imgGyro_dims.height, Bitmap.Config.ARGB_8888);
        this.imgGyro_canvas = new Canvas(this.imgGyro_bitmap);
        this.imgGyro.setImageBitmap(this.imgGyro_bitmap);

        // Pens (Paints)

        // Default black pen
        this.pen_default = new Paint();
        this.pen_default.setAntiAlias(true);
        this.pen_default.setColor(Color.BLACK);
        this.pen_default.setStyle(Paint.Style.STROKE);
        this.pen_default.setStrokeWidth(3f);

        // Pen for the top red dot
        this.pen_point1 = new Paint();
        this.pen_point1.setAntiAlias(true);
        this.pen_point1.setColor(Color.RED);
        this.pen_point1.setStyle(Paint.Style.STROKE);
        this.pen_point1.setStrokeWidth(16f);

        // Pen for the front blue dot
        this.pen_point2 = new Paint();
        this.pen_point2.setAntiAlias(true);
        this.pen_point2.setColor(Color.BLUE);
        this.pen_point2.setStyle(Paint.Style.STROKE);
        this.pen_point2.setStrokeWidth(16f);

        // Calculate the sphere's dimensions
        this.sphereDims = new Vec3();
        this.sphereDims.x = imgGyro_dims.width / 2f; // Origin X
        this.sphereDims.z = imgGyro_dims.height / 2f; // Origin Z (Y on canvas)
        this.sphereDims.y = Math.min(imgGyro_dims.width, imgGyro_dims.height) * 0.6f / 2; // Radius

        // The VGyro instance
        this.vGyro = new VGyro(this, SensorManager.SENSOR_DELAY_GAME);

        // Populate impl selector
        this.availableImpls = this.vGyro.getAvailableImpls();
        List<String> implNames = new ArrayList<>();
        for(VGyroImplInfo info : this.availableImpls)
            implNames.add(info.name);
        this.spnImpls.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, implNames));

        // Select last selected / 1st available impl
        int vGyroImpl = this.settings.getInt(this.getString(R.string.vgyro_pref_impl_key), -1);
        int vGyroImplIdx = vGyroImpl < 0 ? 0 : this.availableImpls.indexOf(VGyroImplInfo.values()[vGyroImpl]);
        this.spnImpls.setSelection(vGyroImplIdx);
        this.setImpl(vGyroImplIdx, (vGyroImpl < 0));

        // Register event listener
        // NOTE: Must after initial selecting to prevent triggering onItemSelected()
        this.spnImpls.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                VGyroImplSelectorActivity.this.setImpl(i, true);
            }

            public void onNothingSelected(AdapterView<?> adapterView) {
                // Impossible (?)
            }
        });

        // Initial draw
        this.resetGyro();
    }

    @Override
    protected void onResume() {
        vGyro.registerListener(this);

        super.onResume();
    }

    @Override
    protected void onPause() {
        vGyro.unregisterListener(this);

        super.onPause();
    }

    public void btnReset_onClick(View v) {
        this.resetGyro();
    }

    public void btnOK_onClick(View v) {
        this.setResult(Activity.RESULT_OK, null);
        this.finish();
    }

    public void onVGyroChanged(VGyroEvent event) {
        // Algorithm from: https://developer.android.google.cn/reference/android/hardware/SensorEvent#values
        float dT = (event.timestamp - this.prevTimestamp) * NS2S;
        Vec3 axis = new Vec3(event.values[0], event.values[1], event.values[2]);

        float angularSpeed = axis.getMagnitude();
        float thetaOverTwo = angularSpeed * dT / 2f;
        float sinThetaOverTwo = (float) Math.sin(thetaOverTwo);
        float cosThetaOverTwo = (float) Math.cos(thetaOverTwo);
        axis = axis.normalize();

        float[] rotation = new float[] {
                sinThetaOverTwo * axis.x,
                sinThetaOverTwo * axis.y,
                sinThetaOverTwo * axis.z,
                cosThetaOverTwo
        };

        this.prevTimestamp = event.timestamp;

        float[] matrix = new float[9];
        SensorManager.getRotationMatrixFromVector(matrix, rotation);
        //matrix = matrix33Multiply(this.prevMatrix, matrix33Transpose(matrix));
        matrix = matrix33Multiply(this.prevMatrix, matrix);
        this.drawGyro(matrix);
        this.prevMatrix = matrix;
    }

}