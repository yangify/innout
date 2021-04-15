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

    private int numVisibleSatellite = -1;
    private float lightValue;

    private TextView resultView, calculationView, walkView, lightView, proximityView, magnetView, magnetVariance;
    private TextView satelliteCountView, satelliteStatusCountView, satelliteCnrMeanView, satelliteCnrVarianceView, satelliteAzimuthView;

    private final boolean isDay = LocalDateTime.now().getHour() >= 7 && LocalDateTime.now().getHour() <= 19;
    private boolean isMoving = false;
    private boolean isCovered = true;

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

//            satelliteStatusCountView.setText("Number of satellite by status: " + numStatusSatellite);
//            satelliteAzimuthView.setText("Proportion more than 90 degree: " + String.format("%.4f", proportionMoreThanNinety));
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
        satelliteCnrMeanView = findViewById(R.id.satelliteCnrMeanView);
        satelliteCnrVarianceView = findViewById(R.id.satelliteCnrVarianceView);
//        satelliteStatusCountView = findViewById(R.id.satelliteStatusCountView);
//        satelliteAzimuthView = findViewById(R.id.satelliteAzimuthView);

        satelliteCountView.setText("Number of satellite: " + null);
        satelliteCnrMeanView.setText("CNR mean: " + null);
        satelliteCnrVarianceView.setText("CNR variance: " + null);
//        satelliteStatusCountView.setText("Number of satellite by status: " + 0);
//        satelliteAzimuthView.setText("Proportion more than 90 degree: " + 0);

        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        resultView = findViewById(R.id.resultView);
        calculationView = findViewById(R.id.calculationView);

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
                isMoving = accelerometerObservatory.getWalkingStatus();
                walkView.setText("Moving?: " + isMoving);
                break;

            case Sensor.TYPE_PROXIMITY:
                // most phone use binary representation for proximity: near/0 or far/5
                isCovered = x == 0;
                proximityView.setText("Covered?: " + isCovered);
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
        double lightWeight = 1.0 / 3;
        double gnssWeight = 1.0 / 3;
        double magnetWeight = 1.0 / 3;

        if (!isMoving) return;
        double lightProb = evaluateLight();
        double gnssProb = evaluateGnss();
        double magnetProb = evaluateMagnet();

        double prob = lightWeight * lightProb + gnssWeight * gnssProb + magnetWeight * magnetProb;
        calculationView.setText(String.format("Formula: (%s * %s) + (%s * %s) + (%s * %s) = %s", lightWeight, lightProb, gnssWeight, gnssProb, magnetWeight, magnetProb, prob));

        if (prob == 0.5) {
            setLoading();
        } else if (prob > 0.5) {
            setIndoor();
        } else {
            setOutdoor();
        }
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Night indoor) 100 - 1000 (Day indoor)
    public double evaluateLight() {
        if (isCovered) return 0.5;
        double mDay = -0.001;
        double cDay = 1.5;
        double probDay = minMax(mDay * lightValue + cDay);

        double mNight = 0.0025;
        double cNight = -0.25;
        double probNight = minMax(mNight * lightValue + cNight);

        return isDay ? probDay : probNight;
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Outdoor) 0-18-30 (Indoor)
    public double evaluateMagnet() {
        Float magnetVariance = magnetometerObservatory.getVariance();
        if (magnetVariance == null) return 0.5;

        double mMagnet = 1.0 / 30;
        double cMagnent = 0;

        return minMax(mMagnet * magnetVariance + cMagnent);
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Indoor) 1-7-15 (Outdoor)
    public double evaluateGnss() {
        if (numVisibleSatellite < 0) return 0.5;
        double mGnss = -0.083333;
        double cGnss = 1.25;
        return minMax(mGnss * numVisibleSatellite + cGnss);
    }

    public double minMax(double score) {
        return Math.min(Math.max(score, 0), 1);
    }

    public void setLoading() {
        resultView.setText("LOADING");
    }

    public void setIndoor() {
        resultView.setText("INDOOR");
    }

    public void setOutdoor() {
        resultView.setText("OUTDOOR");
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
    }
}