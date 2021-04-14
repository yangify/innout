package sg.gov.dsta.innout;

public class AccelerometerObservatory {

    private static AccelerometerObservatory instance;

    private boolean isWalking;

    private double acceleration;
    private double currentObservation;
    private double previousObservation;
    private double delta;

    private int count = 0;
    private double sum = 0;
    private double result = 0;

    private final int SAMPLE_SIZE = 50;     // higher is more precise but slower measure measurement.
    private final double THRESHOLD = 0.3;   // higher is greater movement intensity

    private AccelerometerObservatory() { }

    public static AccelerometerObservatory getInstance() {
        if (instance == null) instance = new AccelerometerObservatory();
        return instance;
    }

    public void log(float observation) {
        previousObservation = currentObservation;
        currentObservation = observation;
        delta = currentObservation - previousObservation;
        acceleration = acceleration * 0.9f + delta;

        if (count <= SAMPLE_SIZE) {
            count++;
            sum += Math.abs(acceleration);
        } else {
            result = sum / SAMPLE_SIZE;
            isWalking = result > THRESHOLD;
            count = 0;
            sum = 0;
            result = 0;
        }
    }

    public boolean getWalkingStatus() {
        return this.isWalking;
    }
}
