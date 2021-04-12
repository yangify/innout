package sg.gov.dsta.innout;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDateTime;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String TAG = "MAIN ACTIVITY";

    private float lightValue, proximityValue, magnetValue;

    private TextView resultView, accelerationView, lightView, proximityView,  magnetView;

    private final boolean isDay = LocalDateTime.now().getHour() >= 7 && LocalDateTime.now().getHour() <= 19;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        resultView = findViewById(R.id.resultView);

        // ACCELEROMETER
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        accelerationView = findViewById(R.id.accelerationView);

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
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x;
        float y;
        float z;

        int sensorType = event.sensor.getType();
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                x = event.values[0];
                y = event.values[1];
                z = event.values[2];
                float accelerationValue = (float) Math.sqrt(x * x + y * y + z * z);
                accelerationView.setText("Acceleration: " + accelerationValue);
                if (accelerationValue >= 10) evaluate();
                break;
            case Sensor.TYPE_PROXIMITY:
                // some phone use binary representation for proximity: near or far
                proximityValue = event.values[0];
                proximityView.setText("Centimeters: " + proximityValue);
                break;
            case Sensor.TYPE_LIGHT:
                lightValue = event.values[0];
                lightView.setText("Lux: " + lightValue);
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                x = event.values[0];
                y = event.values[1];
                z = event.values[2];
                magnetValue = (float) Math.sqrt(x * x + y * y + z * z);
                magnetView.setText("Gauss: " + magnetValue);
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    public void evaluate() {
        evaluateLight();
//        evaluateMagnet();
    }

    public void evaluateLight() {
        if (proximityValue == 0) return;
        if (isDay && lightValue <= 1000 || !isDay && lightValue >= 100) resultView.setText("INDOOR");
        else resultView.setText("OUTDOOR");
    }

    public void evaluateMagnet() {
        if (magnetValue > 80) {
            resultView.setText("OUTDOOR");
        } else {
            resultView.setText("INDOOR");
        }
    }
}