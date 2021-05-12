package innout.innout.observatory;

import java.util.HashMap;

public class MagnetometerObservatory extends TimeBasedObservatory {

    private static MagnetometerObservatory instance;

    private MagnetometerObservatory() {
        super(new HashMap<>());
    }

    public static MagnetometerObservatory getInstance() {
        if (instance == null) instance = new MagnetometerObservatory();
        return instance;
    }
}
