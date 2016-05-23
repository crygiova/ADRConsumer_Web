package fi.aalto.itia.consumer;

import java.util.ArrayList;

import com.google.gson.annotations.Expose;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.models.FridgeModel;

public class StatsAggregator implements Runnable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 3694387787071215724L;
	private static final int FREQ_UPDATES = 3;// in sec

	@Expose
	private ArrayList<Double> aggregatedConsumption;
	@Expose
	private ArrayList<Double> frequency;
	private ArrayList<FridgeModel> models;
	private Double currentAggregatedConsumption;
	private boolean keepGoing = true;

	/**
	 * @param models
	 */
	public StatsAggregator(ArrayList<FridgeModel> models) {
		super();
		this.models = models;
		frequency = new ArrayList<Double>();
		aggregatedConsumption = new ArrayList<Double>();
	}

	@Override
	public void run() {
		while (keepGoing) {
			try {
				Thread.sleep(FREQ_UPDATES * ADR_EM_Common.ONE_SECOND);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			currentAggregatedConsumption = 0d;
			for (FridgeModel fridge : models) {
				currentAggregatedConsumption += fridge
						.getCurrentElectricPower();
			}
			aggregatedConsumption.add(currentAggregatedConsumption);
			frequency.add(FrequencyReader.getCurrentFreqValue());
		}
	}

	public ArrayList<Double> getAggregatedConsumption() {
		return aggregatedConsumption;
	}

	public ArrayList<Double> getFrequency() {
		return frequency;
	}

	public void setKeepGoing(boolean b) {
		keepGoing = b;
	}

}
