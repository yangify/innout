package sg.gov.dsta.innout;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDateTime;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String TAG = "MAIN ACTIVITY";

    private float lightValue, proximityValue;

    private TextView resultView, walkView, lightView, proximityView, magnetView, magnetVariance;

    private final boolean isDay = LocalDateTime.now().getHour() >= 7 && LocalDateTime.now().getHour() <= 19;
    private boolean isWalking = false;
    private boolean isCovered = false;

    private final MagnetObservatory magnetObservatory = MagnetObservatory.getInstance();

    private float[] mGravity;
    private double mAccel;
    private double mAccelCurrent;
    private double mAccelLast;

    private int hitCount = 0;
    private double hitSum = 0;
    private double hitResult = 0;

    private final int SAMPLE_SIZE = 50; // change this sample size as you want, higher is more precise but slow measure.
    private final double THRESHOLD = 0.3; // change this threshold as you want, higher is more spike movement


    private boolean sensorRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        resultView = findViewById(R.id.resultView);

        // ACCELEROMETER
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        walkView = findViewById(R.id.walkView);
        mAccel = 0.00f;
        mAccelCurrent = SensorManager.GRAVITY_EARTH;
        mAccelLast = SensorManager.GRAVITY_EARTH;

        // LIGHT
        Sensor lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        lightView = findViewById(R.id.lightView);

        // PROXIMITY
        Sensor proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
        proximityView = findViewById(R.id.proximityView);

        // MAGNET
        Sensor magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, magnetSensor, SensorManager.SENSOR_DELAY_NORMAL);
        magnetView = findViewById(R.id.magnetView);
        magnetVariance = findViewById(R.id.magnetVariance);

        // TIME
        Handler handler = new Handler();
        int delay = 1000; // 1000 milliseconds == 1 second
        handler.postDelayed(new Runnable() {
            public void run() {
                magnetObservatory.computeAverage();
                magnetObservatory.computeVariance();
                magnetVariance.setText("Magnet variance: " + magnetObservatory.getVariance());
                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x;
        float y;
        float z;

        int sensorType = event.sensor.getType();
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                mGravity = event.values.clone();
                // Shake detection
                x = mGravity[0];
                y = mGravity[1];
                z = mGravity[2];
                mAccelLast = mAccelCurrent;
                mAccelCurrent = Math.sqrt(x * x + y * y + z * z);
                double delta = mAccelCurrent - mAccelLast;
                mAccel = mAccel * 0.9f + delta;
                if (hitCount <= SAMPLE_SIZE) {
                    hitCount++;
                    hitSum += Math.abs(mAccel);
                } else {
                    hitResult = hitSum / SAMPLE_SIZE;
                    isWalking = hitResult > THRESHOLD;
                    hitCount = 0;
                    hitSum = 0;
                    hitResult = 0;
                }
                walkView.setText("Moving?: " + isWalking);
                break;
            case Sensor.TYPE_PROXIMITY:
                // some phone use binary representation for proximity: near or far
                proximityValue = event.values[0];
                proximityView.setText("Centimeters: " + proximityValue);
                isCovered = proximityValue == 0;
                break;
            case Sensor.TYPE_LIGHT:
                lightValue = event.values[0];
                lightView.setText("Lux: " + lightValue);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                x = event.values[0];
                y = event.values[1];
                z = event.values[2];
                float magnetValue = (float) Math.sqrt(x * x + y * y + z * z);
                magnetView.setText("Gauss: " + magnetValue);
                magnetObservatory.log(magnetValue);
                break;
        }
        evaluate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void evaluate() {
        if (!isWalking) return;
        if (isCovered) evaluateMagnet();
        else evaluateLight();
    }

    public void evaluateLight() {
        if (isDay && lightValue <= 1000 || !isDay && lightValue >= 100) resultView.setText("INDOOR");
        else resultView.setText("OUTDOOR");
    }

    public void evaluateMagnet() {
        Float magnetVariance = magnetObservatory.getVariance();
        if (magnetVariance == null) return;
        if (magnetVariance > 18) resultView.setText("INDOOR");
        else resultView.setText("OUTDOOR");
    }
}