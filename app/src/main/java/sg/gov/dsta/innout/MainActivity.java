package sg.gov.dsta.innout;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.telephony.CellInfo;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellLocation;
import android.telephony.CellSignalStrength;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDateTime;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private final String TAG = "MAIN ACTIVITY";

    private TelephonyManager telephonyManager;

    private SensorManager sensorManager;
    private Sensor accelerometer, lightSensor, proximitySensor, magnetSensor;

    private float accelerationValue, lightValue, proximityValue, magnetValue, signalValue;

    private TextView resultView, accelerationView, lightView, proximityView,  magnetView, signalView, numSignalView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // SIGNAL STRENGTH
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        telephonyManager.listen(new PhoneStateListener(), PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        signalView = findViewById(R.id.signalView);
        numSignalView = findViewById(R.id.numSignalView);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        resultView = findViewById(R.id.resultView);

        // ACCELEROMETER
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        accelerationView = findViewById(R.id.accelerationView);

        // LIGHT
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        lightView = findViewById(R.id.lightView);

        // PROXIMITY
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_FASTEST);
        proximityView = findViewById(R.id.proximityView);

        // MAGNET
        magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sensorManager.registerListener(this, magnetSensor, SensorManager.SENSOR_DELAY_NORMAL);
        magnetView = findViewById(R.id.magnetView);
    }

    /*
     * 1. If moving -> evaluate; else -> do nothing
     * 2. If far -> check light, else -> don check light
     * 3.
     */
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
                accelerationValue = (float) Math.sqrt(x * x + y * y + z * z);
                accelerationView.setText("Acceleration: " + accelerationValue);
                if (accelerationValue >= 11) evaluate();
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
        if (isDay() && lightValue <= 1000) resultView.setText("INDOOR");
        if (!isDay() && lightValue >= 100) resultView.setText("OUTDOOR");
    }

    public boolean isDay() {
        return LocalDateTime.now().getHour() >= 7 && LocalDateTime.now().getHour() <= 19;
    }

    public void evaluateMagnet() {
        if (magnetValue > 80) {
            resultView.setText("OUTDOOR");
        } else {
            resultView.setText("INDOOR");
        }
    }

    public void cellRefresh() {
        double average = 0;
        int size = telephonyManager.getAllCellInfo().size();
        for (CellInfo cellInfo: telephonyManager.getAllCellInfo()) {
            if (cellInfo instanceof CellInfoLte) {
                CellInfoLte cellInfoLte = (CellInfoLte) cellInfo;
                CellSignalStrength cellSignalStrength = cellInfoLte.getCellSignalStrength();
                average += cellSignalStrength.getDbm() / size;
            }
            if (cellInfo instanceof CellInfoGsm) {
                CellInfoGsm cellInfoGsm = (CellInfoGsm) cellInfo;
                CellSignalStrength cellSignalStrength = cellInfoGsm.getCellSignalStrength();
                average += cellSignalStrength.getDbm() / size;
            }
            if (cellInfo instanceof CellInfoWcdma) {
                CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cellInfo;
                CellSignalStrength cellSignalStrength = cellInfoWcdma.getCellSignalStrength();
                average += cellSignalStrength.getDbm() / size;
            }
        }
        signalValue = (float) average;
        signalView.setText("dBm: " + average);
        numSignalView.setText("num: " + size);
    }

    class MyPhoneStateListener extends PhoneStateListener {

        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            super.onSignalStrengthsChanged(signalStrength);
            signalValue = signalStrength.getGsmSignalStrength();
            signalValue = (2 * signalValue) - 113; // -> dBm
            signalView.setText("dBm: " + signalValue);
            numSignalView.setText("num: " + signalStrength.getCellSignalStrengths().size());
        }
    }
}