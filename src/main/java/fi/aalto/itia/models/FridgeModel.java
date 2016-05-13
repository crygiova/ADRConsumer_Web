package fi.aalto.itia.models;

import java.util.ArrayList;

import org.apache.commons.math3.analysis.function.Exp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.adrem_consumer.ADRConsumerController;

public class FridgeModel {
	private static final Logger logger = LoggerFactory
			.getLogger(FridgeModel.class);
	private static final int DATA_ACQUISITION_RATE = 60;// once every minute

	private int counter = 0;
	// if false disable the users to keep track of the evolutin of dynamics
	private boolean updateLists = true;

	// COEFFICIENTS OF THE FRIDG
	/** Coefficient of performance */
	private double coeffOfPerf;
	/** Rated Power (w) */
	private double ptcl;
	/** minimum thermal mass (kWh/celcius) */
	private double mcMin;
	/** maximum thermal mass (kWh/celcius) */
	private double mcMax;
	/** thermal conductance (W/C) */
	private double thermalConductanceA;
	/** time constant */
	private double tau;
	/** temperature set-point (celcius) */
	private double temperatureSP;
	/** thermostat bandwidth (celcius) */
	private double thermoBandDT;
	/** ambient temperature */
	private double tAmb;
	/** initial temperature */
	private double t0;
	/** initial on/off state */
	private double q0;

	// FRidgeID
	private static int IDCounter = 0;
	private final int ID;

	// COEFFICIENTS FOR TEMPERATURE EVOLUTION OF THE FRIDGE
	private final Double A;
	private final Double B;
	private final Double E;

	// TODO do I need a long counter???

	// VARIABLES
	// TODO decide if this is a list of keep track only of the current
	// temperature
	/** temperature list */
	private ArrayList<Double> temperatureList;
	/** temperature at a given time t */
	private Double currentTemperature;
	/** power list */
	private ArrayList<Double> electricPowerList;
	/** electric power at a given time t */
	private Double currentElectricPower;
	/** temperature at a given time t */
	private ArrayList<Boolean> onOffList;
	/** temperature at a given time t */
	private boolean currentOnOff;

	/**
	 * @param coeffOfPerf
	 * @param ptcl
	 * @param mcMin
	 * @param mcMax
	 * @param thermalConductanceA
	 * @param tau
	 * @param temperatureSP
	 * @param thermoBandDT
	 * @param tAmb
	 * @param t0
	 * @param q0
	 */
	public FridgeModel(double coeffOfPerf, double ptcl, double mcMin,
			double mcMax, double thermalConductanceA, double tau,
			double temperatureSP, double thermoBandDT, double tAmb, double t0,
			double q0) {
		super();
		this.coeffOfPerf = coeffOfPerf;
		this.ptcl = ptcl;
		this.mcMin = mcMin;
		this.mcMax = mcMax;
		this.thermalConductanceA = thermalConductanceA;
		this.tau = tau;
		this.temperatureSP = temperatureSP;
		this.thermoBandDT = thermoBandDT;
		this.tAmb = tAmb;
		this.t0 = t0;
		this.q0 = q0;

		// FridgeID
		ID = IDCounter++;

		// TODO There is a problem here exp(-dt*tau) where dt is the disc time
		// step!!!!
		// HOw TODO this?? 1 if 1 sec time step!
		this.E = new Exp().value(-tau);
		this.A = 1d - this.E;
		this.B = coeffOfPerf / thermalConductanceA * ptcl;

		this.temperatureList = new ArrayList<Double>();
		this.electricPowerList = new ArrayList<Double>();
		this.onOffList = new ArrayList<Boolean>();
		// Init variables
		this.currentTemperature = t0;
		this.temperatureList.add(t0);
		this.electricPowerList.add(q0);
		
		this.currentElectricPower = q0;
		if (q0 > 0d) {
			// ON
			this.currentOnOff = true;
		} else {
			// OFF
			this.currentOnOff = false;
		}
		this.onOffList.add(this.currentOnOff);
	}

	public static int getIDCounter() {
		return IDCounter;
	}

	public int getID() {
		return ID;
	}

	public double getCoeffOfPerf() {
		return coeffOfPerf;
	}

	public double getPtcl() {
		return ptcl;
	}

	public double getMcMin() {
		return mcMin;
	}

	public double getMcMax() {
		return mcMax;
	}

	public double getThermalConductanceA() {
		return thermalConductanceA;
	}

	public double getTau() {
		return tau;
	}

	public double getTemperatureSP() {
		return temperatureSP;
	}

	public double getThermoBandDT() {
		return thermoBandDT;
	}

	public double gettAmb() {
		return tAmb;
	}

	public double getT0() {
		return t0;
	}

	public double getQ0() {
		return q0;
	}

	public Double getCurrentTemperature() {
		return currentTemperature;
	}

	public Double getCurrentElectricPower() {
		return currentElectricPower;
	}

	public boolean isCurrentOn() {
		return currentOnOff;
	}

	public ArrayList<Double> getTemperatureList() {
		return temperatureList;
	}

	public ArrayList<Double> getElectricPowerList() {
		return electricPowerList;
	}

	public ArrayList<Boolean> getOnOffList() {
		return onOffList;
	}

	// Switch on the fridge
	public void switchOn() {
		this.currentOnOff = true;
		this.currentElectricPower = ptcl;
	}

	// Switch on the fridge
	public void switchOff() {
		this.currentOnOff = false;
		this.currentElectricPower = 0d;
	}

	// Temperature Update Function
	// TODO Olli suggests to add some randomness
	public void updateTemperature() {
		int bufferOnOff = 0;
		if (currentOnOff)
			bufferOnOff = 1;
		this.currentTemperature = this.E * this.currentTemperature + this.A
				* (this.tAmb - this.B * bufferOnOff);
		// only if updatelists is true
		if (updateLists) {
			if (++counter > DATA_ACQUISITION_RATE) {
				counter = 0;
				this.temperatureList.add(this.currentTemperature);
				this.electricPowerList.add(this.currentElectricPower);
				this.onOffList.add(this.currentOnOff);
			}
		}
	}

	public boolean isUpdateLists() {
		return updateLists;
	}

	public void setUpdateLists(boolean updateLists) {
		this.updateLists = updateLists;
	}

	@Override
	public String toString() {
		return "FridgeModel [coeffOfPerf=" + coeffOfPerf + ", ptcl=" + ptcl
				+ ", mcMin=" + mcMin + ", mcMax=" + mcMax
				+ ", thermalConductanceA=" + thermalConductanceA + ", tau="
				+ tau + ", temperatureSP=" + temperatureSP + ", thermoBandDT="
				+ thermoBandDT + ", tAmb=" + tAmb + ", t0=" + t0 + ", q0=" + q0
				+ ", A=" + A + ", B=" + B + ", E=" + E
				+ ", currentTemperature=" + currentTemperature
				+ ", currentElectricPower=" + currentElectricPower
				+ ", currentOnOff=" + currentOnOff + "]";
	}
}
