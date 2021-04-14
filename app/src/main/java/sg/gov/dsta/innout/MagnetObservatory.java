package sg.gov.dsta.innout;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MagnetObservatory {

    private static MagnetObservatory instance;
    private final Map<LocalDateTime, List<Float>> observations;
    private final Integer DELTA = 10;

    private Integer numObservations = 0;
    private float average = 0;
    private Float variance;

    private MagnetObservatory() {
        observations = new HashMap<>();
    }

    public static MagnetObservatory getInstance() {
        if (instance == null) instance = new MagnetObservatory();
        return instance;
    }

    public void computeAverage() {
        if (observations.size() < DELTA) return;
        float sum = 0;
        for (LocalDateTime time : observations.keySet()) {
            List<Float> observationsAtTime = observations.get(time);
            for (Float observation : observationsAtTime) {
                sum += observation;
            }
        }
        average = sum / numObservations;
    }

    public void computeVariance() {
        if (average == 0) return;
        float sum = 0;
        for (LocalDateTime time : observations.keySet()) {
            List<Float> observationsAtTime = observations.get(time);
            for (Float observation : observationsAtTime) {
                sum += Math.pow(observation - average, 2);
            }
        }
        variance = sum / numObservations;
    }

    public void log(float observation) {
        numObservations += 1;
        LocalDateTime currentTime = LocalDateTime.now().withNano(0);
        observations.putIfAbsent(currentTime, new ArrayList<>());
        observations.get(currentTime).add(observation);
        purge(currentTime);
    }

    public void purge(LocalDateTime currentTime) {
        if (observations.size() <= DELTA) return;
        LocalDateTime toBeRemoved = currentTime.minusSeconds(5);
        numObservations -= observations.get(toBeRemoved).size();
        observations.remove(toBeRemoved);
    }

    public Float getVariance() {
        return this.variance;
    }
}
