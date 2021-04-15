package sg.gov.dsta.innout;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.time.LocalDateTime;
import java.util.Collection;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private final String TAG = "MAIN ACTIVITY";

    private int numVisibleSatellite;
    private float lightValue;

    private TextView resultView, walkView, lightView, proximityView, magnetView, magnetVariance;
    private TextView satelliteCountView, satelliteStatusCountView, satelliteCnrMeanView, satelliteCnrVarianceView, satelliteAzimuthView;

    private final boolean isDay = LocalDateTime.now().getHour() >= 7 && LocalDateTime.now().getHour() <= 19;
    private boolean isWalking = false;
    private boolean isCovered = false;

    private final MagnetometerObservatory magnetometerObservatory = MagnetometerObservatory.getInstance();
    private final AccelerometerObservatory accelerometerObservatory = AccelerometerObservatory.getInstance();

    private final GnssMeasurementsEvent.Callback gnssMeasurementsEventCallback = new GnssMeasurementsEvent.Callback() {
        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
            Collection<GnssMeasurement> measurements = event.getMeasurements();
            numVisibleSatellite = measurements.size();

            double cnrAverage = 0;
            for (GnssMeasurement gnssMeasurement : measurements) {
                cnrAverage += gnssMeasurement.getCn0DbHz() / numVisibleSatellite;
            }

            double cnrVariance = 0;
            for (GnssMeasurement gnssMeasurement : measurements) {
                cnrVariance += Math.pow(gnssMeasurement.getCn0DbHz() - cnrAverage, 2) / numVisibleSatellite;
            }

            satelliteCountView.setText("Number of satellite: " + numVisibleSatellite);
            satelliteCnrMeanView.setText("CNR mean: " + cnrAverage);
            satelliteCnrVarianceView.setText("CNR variance: " + cnrVariance);
        }
    };

    private final GnssStatus.Callback gnssStatusCallback = new GnssStatus.Callback() {
        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            int numStatusSatellite = status.getSatelliteCount();

            double moreThanNinety = 0;
            for (int i = 0; i < numStatusSatellite; i++) {
                moreThanNinety += status.getAzimuthDegrees(i) > 90 ? 1 : 0;
            }
            double proportionMoreThanNinety = moreThanNinety / numStatusSatellite;

            satelliteStatusCountView.setText("Number of satellite by status: " + numStatusSatellite);
            satelliteAzimuthView.setText("Proportion more than 90 degree: " + String.format("%.4f", proportionMoreThanNinety));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0, this);

        // GNSS
        locationManager.registerGnssMeasurementsCallback(gnssMeasurementsEventCallback);
        locationManager.registerGnssStatusCallback(gnssStatusCallback);

        satelliteCountView = findViewById(R.id.satelliteCountView);
        satelliteStatusCountView = findViewById(R.id.satelliteStatusCountView);
        satelliteCnrMeanView = findViewById(R.id.satelliteCnrMeanView);
        satelliteCnrVarianceView = findViewById(R.id.satelliteCnrVarianceView);
        satelliteAzimuthView = findViewById(R.id.satelliteAzimuthView);

        satelliteCountView.setText("Number of satellite: " + 0);
        satelliteStatusCountView.setText("Number of satellite by status: " + 0);
        satelliteCnrMeanView.setText("CNR mean: " + 0);
        satelliteCnrVarianceView.setText("CNR variance: " + 0);
        satelliteAzimuthView.setText("Proportion more than 90 degree: " + 0);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        resultView = findViewById(R.id.resultView);

        // ACCELEROMETER
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        walkView = findViewById(R.id.walkView);

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
                magnetometerObservatory.computeAverage();
                magnetometerObservatory.computeVariance();
                magnetVariance.setText("Magnet variance: " + magnetometerObservatory.getVariance());
                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int size = event.values.length;
        float x = size > 0 ? event.values[0] : -1;
        float y = size > 1 ? event.values[1] : -1;
        float z = size > 2 ? event.values[2] : -1;
        double aggregation = Math.sqrt(x * x + y * y + z * z);

        int sensorType = event.sensor.getType();
        switch (sensorType) {
            case Sensor.TYPE_ACCELEROMETER:
                accelerometerObservatory.log((float) aggregation);
                isWalking = accelerometerObservatory.getWalkingStatus();
                walkView.setText("Moving?: " + isWalking);
                break;

            case Sensor.TYPE_PROXIMITY:
                // some phone use binary representation for proximity: near or far
                proximityView.setText("Proximity: " + x);
                isCovered = x == 0;
                break;

            case Sensor.TYPE_LIGHT:
                lightValue = event.values[0];
                lightView.setText("Lux: " + lightValue);
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                float magnetValue = (float) aggregation;
                magnetometerObservatory.log(magnetValue);
                magnetView.setText("Gauss: " + magnetValue);
                magnetometerObservatory.log(magnetValue);
                break;
        }
        evaluate();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    public void evaluate() {
        if (!isWalking) return;
//        if (!isCovered) evaluateLight();
//        else evaluateGnss();
        evaluateGnss();
    }

    public void evaluateLight() {
        if (isDay && lightValue <= 1000 || !isDay && lightValue >= 100) resultView.setText("INDOOR");
        else resultView.setText("OUTDOOR");
    }

    public void evaluateMagnet() {
        Float magnetVariance = magnetometerObservatory.getVariance();
        if (magnetVariance == null) return;
        if (magnetVariance > 18) {
            resultView.setText("INDOOR");
        } else {
            resultView.setText("OUTDOOR");
        }
    }

    public void evaluateGnss() {
        if (numVisibleSatellite >= 8) {
            resultView.setText("OUTDOOR");
        } else {
            resultView.setText("INDOOR");
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
    }
}