package fi.aalto.itia.models;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.itia.consumer.ADRConsumer;

public class FridgeController {

    protected static final Logger logger = LoggerFactory.getLogger(ADRConsumer.class);
    private static final long serialVersionUID = 4328592954437775437L;

    private FridgeModel fridge;

    // from:
    // XXX important since is the restore delay

    // FridgeControl
    private boolean restoreToOff = false;
    private boolean restoreToOn = false;

    public FridgeController(FridgeModel fridge) {
	this.fridge = fridge;
	// random generated delay to restore from DR action
    }

    public FridgeModel getFridge() {
	return fridge;
    }

    public void setFridge(FridgeModel fridge) {
	this.fridge = fridge;
    }

    /**
     * TODO Change this function controls the fridge by means of the defined
     * temperature thresholds.
     * 
     * @return true if the control has been executed / false if no control has
     *         been performed
     */
    public boolean controlFridgeWithThresholds() {
	// T > Tmax and notOn
	if (this.getFridge().getCurrentTemperature() > this.getFridge().getMaxThresholdTemp()
		&& !this.getFridge().isCurrentOn()) {
	    this.getFridge().switchOn();
	    this.setRestoreToOn(false);
	    return true;
	}
	// T < Tmin and On
	if (this.getFridge().getCurrentTemperature() < this.fridge.getMinThresholdTemp()
		&& this.getFridge().isCurrentOn()) {
	    this.getFridge().switchOff();
	    this.setRestoreToOff(false);
	    return true;
	}
	return false;
    }

    public boolean reactDownFrequency() {
	if (this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOff()) {
	    this.getFridge().switchOff();
	    this.setRestoreToOn(true);
	    return true;
	} else {
	    return false;
	}
    }

    public boolean restoreDownFrequency() {
	if (this.getFridge().switchOn()) {
	    logger.info("*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*2*");
	    this.setRestoreToOn(false);
	    return true;
	}
	return false;
    }

    public boolean reactUpFrequency() {
	if (!this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOn()
		&& !this.isRestoreToOff()) {
	    this.getFridge().switchOn();
	    this.setRestoreToOff(true);
	    return true;
	} else {
	    return false;
	}
    }

    public boolean restoreUpFrequency() {
	if (this.getFridge().switchOff()) {
	    logger.info("+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+");
	    this.setRestoreToOff(false);
	    return true;
	}
	return false;
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

}
