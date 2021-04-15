package sg.gov.dsta.innout.observatory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class TimeBasedObservatory {

    private final Map<LocalDateTime, List<Float>> observations;
    private final Integer WINDOW = 5;
    private final Integer DELTA = 10;

    private Integer numObservations = 0;
    private float average = 0;
    private Float variance;

    protected TimeBasedObservatory(Map<LocalDateTime, List<Float>> observations) {
        this.observations = observations;
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
        LocalDateTime toBeRemoved = currentTime.minusSeconds(WINDOW);
        numObservations -= observations.get(toBeRemoved).size();
        observations.remove(toBeRemoved);
    }

    public Float getVariance() {
        return this.variance;
    }
}
