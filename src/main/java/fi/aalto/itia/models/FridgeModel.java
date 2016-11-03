package fi.aalto.itia.models;

import java.util.ArrayList;
import java.util.Random;

import org.apache.commons.math3.analysis.function.Exp;
import org.apache.commons.math3.analysis.function.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.annotations.Expose;

import fi.aalto.itia.consumer.ADRConsumer;

public class FridgeModel {
    private static final Logger logger = LoggerFactory.getLogger(FridgeModel.class);
    private static final int DATA_ACQUISITION_RATE = 3;// 60 once every minute
    // constant minimum delay before changing status for a fridge
    private static final int CONST_DELAY_BEFORE_CHANGING_STATUS = 40;
    //time delay before being capable to change status again
    private int delayBeforeChangingStatus;

    public void setDelayBeforeChangingStatus(int delayBeforeChangingStatus) {
        this.delayBeforeChangingStatus = delayBeforeChangingStatus;
    }

    private int counterDataAquisitionRate = 0;
    private int counterDelayBeforeChangingStatus = 0;
    private boolean possibleToSwitchOff = true;
    private boolean possibleToSwitchOn = true;
    private boolean useOnlyTimeDelay = true;
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
    @Expose
    private ArrayList<Double> temperatureList;
    /** temperature at a given time t */
    private Double currentTemperature;
    /** power list */
    @Expose
    private ArrayList<Double> electricPowerList;
    /** electric power at a given time t */
    private Double currentElectricPower;
    /** temperature at a given time t */
    @Expose
    private ArrayList<Boolean> onOffList;
    /** temperature at a given time t */
    private boolean currentOnOff;

    private ADRConsumer consumer;

    static {
    }

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
    public FridgeModel(double coeffOfPerf, double ptcl, double mcMin, double mcMax,
	    double thermalConductanceA, double tau, double temperatureSP, double thermoBandDT,
	    double tAmb, double t0, double q0) {
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
	// 180 sec + rand 120max, = max 300 sec
	Random r = new Random();
	// TODO CHANGE
	// DELAY_BEFORE_CHANGING_STATUS = 180 + Math.round(120 * r.nextFloat());
	delayBeforeChangingStatus = CONST_DELAY_BEFORE_CHANGING_STATUS
		+ Math.round(CONST_DELAY_BEFORE_CHANGING_STATUS * r.nextFloat());

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

    // Switch on the fridge
    public boolean switchOn() {
	if (possibleToSwitchOn) {
	    this.currentOnOff = true;
	    this.currentElectricPower = ptcl;
	    this.setPossibleToSwitchOff(false);
	    this.setPossibleToSwitchOn(false);
	    counterDelayBeforeChangingStatus = 0;
	    return true;
	}
	return false;
    }

    // Switch on the fridge
    public boolean switchOff() {
	if (possibleToSwitchOff) {
	    this.currentOnOff = false;
	    this.currentElectricPower = 0d;
	    this.setPossibleToSwitchOff(false);
	    this.setPossibleToSwitchOn(false);
	    counterDelayBeforeChangingStatus = 0;
	    return true;
	}
	return false;

    }

    public boolean switchOnOff(boolean on) {
	if (on) {
	    return switchOn();
	} else {
	    return switchOff();
	}
    }

    // Temperature Update Function
    // TODO Olli suggests to add some randomness
    // TODO a certain number of updates is needed beforechanging status again
    public void updateTemperature() {
	int bufferOnOff = 0;
	if (currentOnOff)
	    bufferOnOff = 1;
	this.currentTemperature = this.E * this.currentTemperature + this.A
		* (this.tAmb - this.B * bufferOnOff);

	if (!possibleToSwitchOff || !possibleToSwitchOn) {
	    // if the time delay has passed or
	    // changes
	    if (++counterDelayBeforeChangingStatus >= delayBeforeChangingStatus) {
		// if current on -> I can switch off after delay
		// if current off -> I can switch on after delay
		if (this.currentOnOff)
		    this.setPossibleToSwitchOff(true);
		else
		    this.setPossibleToSwitchOn(true);
		this.useOnlyTimeDelay = false;
	    }
	    // if the temperature is out of the
	    // temperature limits! Allow status
	    if (this.currentTemperature > (this.getTemperatureSP() + this.getThermoBandDT())
		    && !this.useOnlyTimeDelay) {
		this.useOnlyTimeDelay = true;
		this.setPossibleToSwitchOn(true);
	    }
	    if (this.currentTemperature < (this.getTemperatureSP() - this.getThermoBandDT())
		    && !this.useOnlyTimeDelay) {
		this.useOnlyTimeDelay = true;
		this.setPossibleToSwitchOff(true);
	    }
	}
	// only if updatelists is true
	if (updateLists) {
	    if (++counterDataAquisitionRate > DATA_ACQUISITION_RATE) {
		counterDataAquisitionRate = 0;
		this.temperatureList.add(this.currentTemperature);
		this.electricPowerList.add(this.currentElectricPower);
		this.onOffList.add(this.currentOnOff);
	    }
	}
    }

    public double getSecondsToTempMaxLimit() {
	double maxTemp = this.getTemperatureSP() + this.getThermoBandDT();
	double num, den;
	// double num = (maxTemp - this.tAmb + this.coeffOfPerf
	// * (this.currentElectricPower / this.thermalConductanceA));
	// double den = (this.currentTemperature - this.tAmb + this.coeffOfPerf
	// * (this.currentElectricPower / this.thermalConductanceA));
	num = (maxTemp - this.tAmb + this.coeffOfPerf * (this.ptcl / this.thermalConductanceA));
	den = (this.currentTemperature - this.tAmb + this.coeffOfPerf
		* (this.ptcl / this.thermalConductanceA));
	double log = new Log().value(num / den);
	// minus away here to get a positive number
	return (1 / tau) * log;
    }

    public double getSecondsToTempMinLimit() {
	double minTemp = this.getTemperatureSP() - this.getThermoBandDT();
	double num, den;
	// double num = (minTemp - this.tAmb + this.coeffOfPerf
	// * (this.currentElectricPower / this.thermalConductanceA));
	// double den = (this.currentTemperature - this.tAmb + this.coeffOfPerf
	// * (this.currentElectricPower / this.thermalConductanceA));
	num = (minTemp - this.tAmb + this.coeffOfPerf * (this.ptcl / this.thermalConductanceA));
	den = (this.currentTemperature - this.tAmb + this.coeffOfPerf
		* (this.ptcl / this.thermalConductanceA));
	double log = new Log().value(num / den);
	return (-1 / tau) * log;
    }

    /**
     * This method returns the max temperature threshold for the fridge.
     * 
     * @return
     */
    public double getMaxThresholdTemp() {
	return this.getTemperatureSP() + this.getThermoBandDT();
    }

    /**
     * This method returns the min temperature threshold for the fridge.
     * 
     * @return
     */
    public double getMinThresholdTemp() {
	return this.getTemperatureSP() - this.getThermoBandDT();
    }

    public boolean isUpdateLists() {
	return updateLists;
    }

    public void setUpdateLists(boolean updateLists) {
	this.updateLists = updateLists;
    }

    public int getDelayBeforeChangingStatus() {
	return delayBeforeChangingStatus;
    }

    public ADRConsumer getConsumer() {
	return consumer;
    }

    public void setConsumer(ADRConsumer consumer) {
	this.consumer = consumer;
    }

    @Override
    public String toString() {
	return "FridgeModel [coeffOfPerf=" + coeffOfPerf + ", ptcl=" + ptcl + ", mcMin=" + mcMin
		+ ", mcMax=" + mcMax + ", thermalConductanceA=" + thermalConductanceA + ", tau="
		+ tau + ", temperatureSP=" + temperatureSP + ", thermoBandDT=" + thermoBandDT
		+ ", tAmb=" + tAmb + ", t0=" + t0 + ", q0=" + q0 + ", A=" + A + ", B=" + B + ", E="
		+ E + ", currentTemperature=" + currentTemperature + ", currentElectricPower="
		+ currentElectricPower + ", currentOnOff=" + currentOnOff + "]";
    }

    public boolean isPossibleToSwitchOff() {
	return possibleToSwitchOff;
    }

    public void setPossibleToSwitchOff(boolean possibleToSwitchOff) {
	this.possibleToSwitchOff = possibleToSwitchOff;
    }

    public boolean isPossibleToSwitchOn() {
	return possibleToSwitchOn;
    }

    public void setPossibleToSwitchOn(boolean possibleToSwitchOn) {
	this.possibleToSwitchOn = possibleToSwitchOn;
    }

    public boolean isUseOnlyTimeDelay() {
	return useOnlyTimeDelay;
    }

    public void setUseOnlyTimeDelay(boolean useOnlyTimeDelay) {
	this.useOnlyTimeDelay = useOnlyTimeDelay;
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

}
