package fi.aalto.itia.consumer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.adrem_consumer.ADRConsumerController;

public class FrequencyReader implements Runnable {

	// TODO This class reads the frequency values from the aggregator.
	// TODO should this class keep track of the values for monitoring purposes??
	private static final Logger logger = LoggerFactory
			.getLogger(FrequencyReader.class);
	private static final String URL_FREQ_MEASURE = "http://localhost:8080/itia/frequency";
	private static final int FREQ_READING = 3000;// Every 3 seconds
	private static Double NOMINAL_FREQ = 50d;

	private static FrequencyReader singleton;
	private static AtomicReference<Double> currentFreqValue = new AtomicReference<Double>();

	private static boolean keepReading;
	private static URL frequencyProducerURL;
	private static BufferedReader frequencyReader;

	private static Thread freq_t;

	private FrequencyReader() {
		keepReading = true;
		try {
			frequencyProducerURL = new URL(URL_FREQ_MEASURE);
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
		currentFreqValue.set(NOMINAL_FREQ);
	}

	public static FrequencyReader startFrequencyReader() {
		if (singleton == null) {
			singleton = new FrequencyReader();
			freq_t = new Thread(singleton);
			freq_t.start();
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
					
					logger.info(currentFreqValue.get().toString());
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
		}
	}

	public static Double getCurrentFreqValue() {
		return currentFreqValue.get();
	}

	public static boolean isKeepReading() {
		return keepReading;
	}

	public static void setKeepReadingToFalse() {
		FrequencyReader.keepReading = false;
	}

}
