package sg.gov.dsta.innout;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssMeasurement;
import android.location.GnssMeasurementsEvent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import sg.gov.dsta.innout.observatory.AccelerometerObservatory;
import sg.gov.dsta.innout.observatory.MagnetometerObservatory;

public class MainActivity extends AppCompatActivity implements SensorEventListener, LocationListener {

    private final String TAG = "MAIN ACTIVITY";

    private double gnssProb, lightProb, magnetProb, wifiProb = 0.5;

    private int numVisibleSatellite = -1;
    private double cnrAverage = 0.0;
    private double cnrVariance = 0.0;
    private float lightValue;
    private int numVisibleAp = -1;
    private double meanWifiStrength = -1;

    private TextView resultView, calculationView, walkView, proximityView;
    private TextView lightView, magnetView, magnetVarianceView;
    private TextView satelliteCountView, satelliteCnrMeanView, satelliteCnrVarianceView;
    private TextView wifiCountView, wifiAverageStrengthView;

    private final boolean isDay = LocalDateTime.now().getHour() >= 7 && LocalDateTime.now().getHour() <= 19;
    private boolean isMoving = false;
    private boolean isCovered = false;

    private final MagnetometerObservatory magnetometerObservatory = MagnetometerObservatory.getInstance();
    private final AccelerometerObservatory accelerometerObservatory = AccelerometerObservatory.getInstance();

    private WifiManager wifiManager;

    private final GnssMeasurementsEvent.Callback gnssMeasurementsEventCallback = new GnssMeasurementsEvent.Callback() {
        @Override
        public void onGnssMeasurementsReceived(GnssMeasurementsEvent event) {
            Collection<GnssMeasurement> measurements = event.getMeasurements();
            numVisibleSatellite = measurements.size();

            cnrAverage = 0;
            for (GnssMeasurement gnssMeasurement : measurements) {
                cnrAverage += gnssMeasurement.getCn0DbHz() / numVisibleSatellite;
            }

            cnrVariance = 0;
            for (GnssMeasurement gnssMeasurement : measurements) {
                cnrVariance += Math.pow(gnssMeasurement.getCn0DbHz() - cnrAverage, 2) / numVisibleSatellite;
            }

            satelliteCountView.setText("Number of satellite: " + numVisibleSatellite);
            satelliteCnrMeanView.setText("CNR mean: " + cnrAverage);
            satelliteCnrVarianceView.setText("CNR variance: " + cnrVariance);
        }
    };

    BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent intent) {
            boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
            if (!success) return;
            List<ScanResult> scanResults = wifiManager.getScanResults();

            numVisibleAp = scanResults.size();
            meanWifiStrength = 0.0;
            for (ScanResult result : scanResults) {
                meanWifiStrength += (double) result.level / numVisibleAp;
            }

            wifiCountView.setText("Number of Wifi: " + numVisibleAp);
            wifiAverageStrengthView.setText("Average wifi strength: " + meanWifiStrength);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // WIFI
        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        wifiCountView = findViewById(R.id.wifiCountView);
        wifiAverageStrengthView = findViewById(R.id.wifiAverageStrengthView);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiScanReceiver, intentFilter);
        wifiManager.startScan();

        wifiCountView.setText("Number of Wifi: " + null);
        wifiAverageStrengthView.setText("Average wifi strength: " + null);

        // GNSS
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10, 0, this);
        locationManager.registerGnssMeasurementsCallback(gnssMeasurementsEventCallback);

        satelliteCountView = findViewById(R.id.satelliteCountView);
        satelliteCnrMeanView = findViewById(R.id.satelliteCnrMeanView);
        satelliteCnrVarianceView = findViewById(R.id.satelliteCnrVarianceView);

        satelliteCountView.setText("Number of satellite: " + null);
        satelliteCnrMeanView.setText("CNR mean: " + null);
        satelliteCnrVarianceView.setText("CNR variance: " + null);

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
        magnetVarianceView = findViewById(R.id.magnetVarianceView);

        // TIME
        Handler handler = new Handler();
        int delay = 1000; // 1000 milliseconds == 1 second
        handler.postDelayed(new Runnable() {
            public void run() {
                magnetometerObservatory.computeAverage();
                magnetometerObservatory.computeVariance();
                magnetVarianceView.setText("Magnet variance: " + magnetometerObservatory.getVariance());
                wifiManager.startScan();
//                logData();
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
                lightValue = x;
                lightView.setText("Lux: " + x);
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
        if (!isMoving) return;

        double lightWeight = 0.295;
        double gnssWeight = 0.370;
        double magnetWeight = 0.130;
        double wifiWeight = 0.205;

        evaluateSensors();

        double prob = lightWeight * lightProb + gnssWeight * gnssProb + magnetWeight * magnetProb + wifiWeight * wifiProb;
        calculationView.setText(String.format("Gnss: (%s * %s) + \nLight: (%s * %s) + \nMagnet: (%s * %s) + \nWifi: (%s * %s) = \nTotal: %s", gnssProb, gnssWeight, lightProb, lightWeight, magnetProb, magnetWeight, wifiProb, wifiWeight, prob));

        if (prob == 0.5) {
            setLoading();
        } else if (prob > 0.5) {
            setIndoor();
        } else {
            setOutdoor();
        }
    }

    public void evaluateSensors() {
        evaluateLight();
        evaluateGnss();
        evaluateMagnet();
        evaluateWifi();
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Night indoor) 100 - 1000 (Day indoor)
    public void evaluateLight() {
        if (isCovered) return;

        double mDay = -0.001;
        double cDay = 1.5;
        double probDay = minMax(mDay * lightValue + cDay);

        double mNight = 0.0025;
        double cNight = -0.25;
        double probNight = minMax(mNight * lightValue + cNight);

        lightProb = isDay ? probDay : probNight;
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Outdoor) 0-18-30 (Indoor)
    public void evaluateMagnet() {
        Float magnetVariance = magnetometerObservatory.getVariance();

        double mMagnet = 1.0 / 30;
        double cMagnent = 0;

        magnetProb = magnetVariance != null ? minMax(mMagnet * magnetVariance + cMagnent) : 0.5;
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Indoor) 1-7-15 (Outdoor) - Count
    // Threshold: (Indoor) 20 - 27.5 - 35 (Outdoor) - MeanCnr
    public void evaluateGnss() {
        double countWeight = 0.9;
        double mCount = -0.083333;
        double cCount = 1.25;
        double countProb = countWeight * (numVisibleSatellite < 0 ? 0.5 : minMax(mCount * numVisibleSatellite + cCount));

        double cnrWeight = 0.1;
        double mCnr = -1.0 / 15;
        double cCnr = 7.0 / 3;
        double cnrProb = cnrWeight * (cnrAverage < 0 ? 0.5 : minMax(mCnr * cnrAverage + cCnr));

        gnssProb = countProb + cnrProb;
    }

    // indoor = 1; outdoor = 0
    // Threshold: (Indoor) 0-25-50 (Outdoor)
    // Threshold: (Indoor) 75-82.5-90 (Outdoor)
    public void evaluateWifi() {
        double countWeight = 0.5;
        double mWifiCount = -0.02;
        double cWifiCount = 1;
        double countProb = countWeight * minMax(mWifiCount * numVisibleAp + cWifiCount);

        double strengthWeight = 0.5;
        double mWifiStrength = 1.0 / 15;
        double cWifiStrength = 6;
        double strengthProb = strengthWeight * minMax(mWifiStrength * meanWifiStrength + cWifiStrength);

        wifiProb = meanWifiStrength == -1 ? 0.5 : minMax(mWifiStrength * meanWifiStrength + cWifiStrength);
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

    private void logData() {
        String data = LocalDateTime.now().withNano(0) +
                "," +
                numVisibleSatellite +
                "," +
                cnrAverage +
                "," +
                cnrVariance +
                "," +
                lightValue +
                "," +
                magnetometerObservatory.getVariance() +
                "," +
                numVisibleAp +
                "," +
                meanWifiStrength;
        writeToFile(data + "\n");
    }

    private void writeToFile(String data) {
        File path = getFilesDir();
        File file = new File(path, "data.csv");
        try (FileOutputStream stream = new FileOutputStream(file, true)) {
            stream.write(data.getBytes());
        } catch (IOException e) {
            Log.d(TAG, e.getMessage());
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
    }
}