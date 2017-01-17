package fi.aalto.itia.consumer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.taglibs.standard.lang.jstl.Coercions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;

public class FrequencyReader implements Runnable {

    // This class reads the frequency values from the aggregator.
    private static final Logger logger = LoggerFactory.getLogger(FrequencyReader.class);
    private static final String URL_FREQ_MEASURE = "http://localhost:8080/itia/frequency";
    private static final int FREQ_READING = ADR_EM_Common.ONE_SECOND;// Every second
    private static final double TARGET_FLEX = ADR_EM_Common.TARGET_FLEX;
    public static final double NOMINAL_FREQ = 50d;
    private static double DEAD_BAND = 0.02d;
    private static final double MAX_FREQ_DEV = 0.1d;

    private static double filteredFrequency = NOMINAL_FREQ;

    // private double coeff = 0.5d;
    // Gamma is in seconds
    private static double gamma = 180d;
    private static double betaCoeff = -gamma / Math.log(1 - 0.95);
    private static double coeff = Math.exp(-1 / betaCoeff);

    private static FrequencyReader singleton;
    private static AtomicReference<Double> currentFreqValue = new AtomicReference<Double>();

    private static boolean keepReading;
    private static boolean filterFrequency;
    private static URL frequencyProducerURL;
    private static BufferedReader frequencyReader;

    private static Thread freq_t;

    // inject errors in the frequency filtered
    private static boolean errors = false;
    private static double perErrors = 0d;

    private FrequencyReader() {
	keepReading = true;
	try {
	    frequencyProducerURL = new URL(URL_FREQ_MEASURE);
	} catch (MalformedURLException e) {
	    e.printStackTrace();
	}
	currentFreqValue.set(NOMINAL_FREQ);
    }

    public static FrequencyReader startFrequencyReader( boolean _filterFreq, boolean frerrors, double perFRErrors) {
	if (singleton == null) {
	    singleton = new FrequencyReader();
	    freq_t = new Thread(singleton);
	    freq_t.start();
	}
	filterFrequency = _filterFreq;
	errors = frerrors;
	if (errors) {
	    perErrors = perFRErrors;
	}
	return singleton;
    }

    @Override
    public void run() {
	String line;
	while (keepReading) {
	    // TODO get the frequency value from the grid url
	    try {
		frequencyReader = new BufferedReader(new InputStreamReader(
			frequencyProducerURL.openStream()));
		while ((line = frequencyReader.readLine()) != null) {
		    currentFreqValue.set(Double.valueOf(line));
		}

	    } catch (IOException e) {
		e.printStackTrace();
		// in case of exception set to nominal freq value
		currentFreqValue.set(NOMINAL_FREQ);
	    }
	    try {
		Thread.sleep(FREQ_READING);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	    filteredFrequency = coeff * filteredFrequency + (1 - coeff) * currentFreqValue.get();

	    logger.info(currentFreqValue.get().toString() + " - FF -" + filteredFrequency);
	}
    }

    public static double getFilteredFrequency() {
	// TODO in order to work with errors the frequency should be altered at
	// the consumer level not at the frequency reader level
	if (errors && Math.random() < perErrors) {
	    // return an error filtered frequency
	    return Math.signum(Math.random() - 0.5d) * filteredFrequency - (0.01);
	}
	if(!filterFrequency)
	{
	    //if required to not filter the frequency
	    return getCurrentFreqValue();
	}
	return filteredFrequency;
    }

    public static void setFilteredFrequency(double filteredFrequency) {
	FrequencyReader.filteredFrequency = filteredFrequency;
    }

    public static Double getCurrentFreqValue() {
	return currentFreqValue.get();
    }

    public static boolean isKeepReading() {
	return keepReading;
    }

    public static double calculateDesiredADR(double baseLevel) {
	double newDesiredADR = 0d;
	double freqDeviation = filteredFrequency - NOMINAL_FREQ;
	double adrBand = MAX_FREQ_DEV - DEAD_BAND;// 0.08
	double absFreqDeviation = Math.abs(freqDeviation);
	if (absFreqDeviation < DEAD_BAND) {
	    newDesiredADR = 0d;
	} else if (absFreqDeviation > MAX_FREQ_DEV) {
	    newDesiredADR = Math.signum(freqDeviation) * TARGET_FLEX;
	} else {
	    // in the reaction band
	    newDesiredADR = Math.signum(freqDeviation) * (absFreqDeviation - DEAD_BAND) * TARGET_FLEX
		    / (MAX_FREQ_DEV - DEAD_BAND);
	}

	return baseLevel + newDesiredADR;
    }

    public static void setKeepReadingToFalse() {
	FrequencyReader.keepReading = false;
	singleton = null;
    }

}
