package fi.aalto.itia.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.models.FridgeModel;

import org.apache.commons.math3.distribution.BetaDistribution;

public class ADRConsumer extends SimulationElement {
    /**
	 * 
	 */
    protected static final Logger logger = LoggerFactory.getLogger(ADRConsumer.class);
    private static final long serialVersionUID = 4328592954437775437L;
    private static final String PREFIX_INPUT_QUEUE = "adrc_";
    protected static final long MAX_TIME_FREQ_UPDATE = 3 * ADR_EM_Common.ONE_MIN;
    private static final int RESTORE_DELAY_CONST = 0;
    private static final int RESTORE_DELAY_VAR = 15;

    private FridgeModel fridge;
    private final int ID;

    // from:
    // http://commons.apache.org/proper/commons-math/userguide/distribution.html
    private BetaDistribution betaD = new BetaDistribution(3, 7);
    // XXX important since is the restore delay
    protected int restoreDelay;
    // XXX important since is the react delay
    protected int reactDelay;

    // FridgeControl
    private boolean restoreToOff = false;
    private boolean restoreToOn = false;
    private int counterRestore = 0;
    private int counterReact = 0;

    public ADRConsumer(int ID, FridgeModel fridge) {
	super(PREFIX_INPUT_QUEUE + ID);
	this.ID = ID;
	this.fridge = fridge;

	// random generated delay to restore from DR action
	this.restoreDelay = (int) (RESTORE_DELAY_CONST + Math.round(RESTORE_DELAY_VAR
		* betaD.sample()));
	this.reactDelay = (int) (RESTORE_DELAY_CONST + Math.round(RESTORE_DELAY_VAR
		* betaD.sample()));

    }

    public FridgeModel getFridge() {
	return fridge;
    }

    public int getID() {
	return ID;
    }

    public void setFridge(FridgeModel fridge) {
	this.fridge = fridge;
    }

    // TODO init procedure of the consumer
    // TODO Registration message (it could be that in the content of the
    // registration message there is already the first update
    // TODO think how to monitor the frequency (it could be done centrally
    // by he main consumer every second and there could be a method to call)

    // TODO decide also how often the updates are sent, and with which
    // policy (frequency & every status change?)
    // if registration ok then proceed to the loop, start by controlling
    // with temperature limits, then with instructions
    // init register
    // TODO get first UpdateMessageContent, register with random update
    // message
    // fridgeON
    protected boolean controlFridgeWithThresholds() {
	// CONTROL Of the FRIDGE using the conditions of the fridges
	// T > Tmax and notOn
	if (this.getFridge().getCurrentTemperature() > (this.getFridge().getTemperatureSP() + this
		.getFridge().getThermoBandDT()) && !this.getFridge().isCurrentOn()) {
	    this.getFridge().switchOn();
	    this.setRestoreToOn(false);
	    this.initCounterRestore();
	    return true;
	}
	// T < Tmin and On
	if (this.getFridge().getCurrentTemperature() < (this.getFridge().getTemperatureSP() - this
		.getFridge().getThermoBandDT()) && this.getFridge().isCurrentOn()) {
	    this.getFridge().switchOff();
	    this.setRestoreToOff(false);
	    this.initCounterRestore();
	    return true;
	}
	return false;
    }

    public int getCounterRestoreMax() {
	return restoreDelay;
    }

    public void setCounterRestoreMax(int counterRestoreMax) {
	this.restoreDelay = counterRestoreMax;
    }

    public boolean isRestoreToOn() {
	return restoreToOn;
    }

    public void setRestoreToOn(boolean restoreToOn) {
	this.restoreToOn = restoreToOn;
    }

    public boolean isRestoreToOff() {
	return restoreToOff;
    }

    public void setRestoreToOff(boolean restoreToOff) {
	this.restoreToOff = restoreToOff;
    }

    public int getCounterRestore() {
	return counterRestore;
    }

    public void addCounterRestore() {
	counterRestore++;
    }

    public void initCounterRestore() {
	counterRestore = 0;
    }

    //Reaction delay methods
    public int getReactDelay() {
	return reactDelay;
    }

    public void setReactDelay(int reactDelay) {
	this.reactDelay = reactDelay;
    }

    public int getCounterReact() {
	return counterReact;
    }

    public void addCounterReact() {
	counterReact++;
    }

    public void initCounterReact() {
	counterReact = 0;
    }

    @Override
    public void scheduleTasks() {
	// TODO Auto-generated method stub

    }

    @Override
    public void executeTasks() {
	// TODO Auto-generated method stub

    }

    @Override
    public void elaborateIncomingMessages() {
	// TODO Auto-generated method stub

    }

    @Override
    public void run() {

    }

}
