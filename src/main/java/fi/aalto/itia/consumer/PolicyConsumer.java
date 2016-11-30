package fi.aalto.itia.consumer;

import java.util.Random;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.models.FridgeModel;
import fi.aalto.itia.util.Utility;

public class PolicyConsumer extends ADRConsumer {

    /**
     * 
     */
    private static final long serialVersionUID = 1420291607458821672L;
    private Double[] freqToReactUnderArray = { 49.98, 49.96, 49.94, 49.92, 49.90 };
    private Double[] freqToReactAboveArray = { 50.02, 50.04, 50.06, 50.08, 50.1 };
    private Double freqToReactUnder;
    private Double freqToReactAbove;

    public PolicyConsumer(int ID, FridgeModel fridge) {
	super(ID, fridge);
	Random dice5 = new Random();
	freqToReactUnder = freqToReactUnderArray[dice5.nextInt(5)];
	freqToReactAbove = freqToReactAboveArray[dice5.nextInt(5)];
    }

    @Override
    public void run() {
	// INITIAL DELAY
	try {
	    Thread.sleep(30 * ADR_EM_Common.ONE_SECOND);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}

	// Loop keep going
	while (this.keepGoing) {
	    // XXX this is the control
	    if (!this.getFridgeController().controlFridgeWithThresholds()) {
		// applyFreqControl(freqToReactUnder, freqToReactAbove);
		//applyFreqControlV2(freqToReactUnder, freqToReactAbove);
	    }
	    // check if we need to send a new update with last update sent
	    try {
		Thread.sleep(Math.round((2 + 3 * Utility.getRandom()) * ADR_EM_Common.ONE_SECOND));
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    /*
     * @Autowired
     * 
     * @Deprecated private void applyFreqControl(Double freqToReactUnder, Double
     * freqToReactAbove) {
     * 
     * if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <=
     * freqToReactUnder && !this.isRestoreToOn()) { if
     * (this.getFridge().isCurrentOn() &&
     * this.getFridge().isPossibleToSwitchOff()) { this.getFridge().switchOff();
     * this.setRestoreToOn(true); } this.initCounterRestore(); } else if
     * (this.isRestoreToOn()) { if (freqToReactUnder != 0d &&
     * FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
     * this.initCounterRestore(); } else { this.addCounterRestore(); } if
     * (this.getCounterRestore() > restoreDelay) { // restoreOn
     * logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
     * + this.restoreDelay + " - counter " + this.getCounterRestore());
     * this.setRestoreToOn(false); this.getFridge().switchOn();
     * this.initCounterRestore(); } }
     * 
     * // 2// if (freqToReactAbove != 0d &&
     * FrequencyReader.getCurrentFreqValue() >= freqToReactAbove &&
     * !this.isRestoreToOff()) { if (!this.getFridge().isCurrentOn() &&
     * this.getFridge().isPossibleToSwitchOn() && !this.isRestoreToOff()) {
     * this.getFridge().switchOn(); this.setRestoreToOff(true); // counting how
     * many times it is reacting to up frequency
     * 
     * } this.initCounterRestore(); } else if (this.isRestoreToOff()) { if
     * (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >=
     * freqToReactAbove) { this.initCounterRestore(); } else {
     * this.addCounterRestore(); } if (this.getCounterRestore() > restoreDelay)
     * {// restoreOff logger.info(this.inputQueueName +
     * " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+" + this.restoreDelay +
     * " - counter " + this.getCounterRestore()); this.setRestoreToOff(false);
     * this.getFridge().switchOff(); this.initCounterRestore(); } } }
     */
    /**
     * Second version of frequency control for independent consumers
     * 
     * @param freqToReactUnder
     * @param freqToReactAbove
     *
    private void applyFreqControlV2(Double freqToReactUnder, Double freqToReactAbove) {

	if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
		&& !this.getFridgeController().isRestoreToOn()) {
	    this.getFridgeController().addCounterReact();
	    // if it is time to react
	    if (this.getFridgeController().getCounterReact() > this.getFridgeController()
		    .getReactDelay()) {
		if (this.getFridgeController().getFridge().isCurrentOn()
			&& this.getFridgeController().getFridge().isPossibleToSwitchOff()) {
		    this.getFridgeController().getFridge().switchOff();
		    this.getFridgeController().setRestoreToOn(true);
		    // counting how many times it is reacting to Down frequency
		}
		this.getFridgeController().initCounterReact();
	    }
	    this.getFridgeController().initCounterRestore();
	} else if (this.getFridgeController().isRestoreToOn()) {
	    if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
		this.getFridgeController().initCounterRestore();
	    } else {
		this.getFridgeController().addCounterRestore();
	    }
	    if (this.getFridgeController().getCounterRestore() > this.getFridgeController()
		    .getRestoreDelay() || freqToReactUnder == 0d) { // restoreOn
		if (this.getFridgeController().getFridge().switchOn()) {
		    logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
			    + this.getFridgeController().getRestoreDelay() + " - counter "
			    + this.getFridgeController().getCounterRestore());
		    this.getFridgeController().setRestoreToOn(false);
		    this.getFridgeController().initCounterRestore();
		}
	    }
	}

	// 2//
	if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
		&& !this.getFridgeController().isRestoreToOff()) {
	    this.getFridgeController().addCounterReact();
	    // if it is time to react
	    if (this.getFridgeController().getCounterReact() > this.getFridgeController()
		    .getReactDelay()) {
		if (!this.getFridgeController().getFridge().isCurrentOn()
			&& this.getFridgeController().getFridge().isPossibleToSwitchOn()
			&& !this.getFridgeController().isRestoreToOff()) {
		    this.getFridgeController().getFridge().switchOn();
		    this.getFridgeController().setRestoreToOff(true);
		    // counting how many times it is reacting to up frequency
		}
		this.getFridgeController().initCounterReact();
	    }
	    this.getFridgeController().initCounterRestore();
	} else if (this.getFridgeController().isRestoreToOff()) {
	    if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.getFridgeController().initCounterRestore();
	    } else {
		this.getFridgeController().addCounterRestore();
	    }
	    if (this.getFridgeController().getCounterRestore() > this.getFridgeController()
		    .getRestoreDelay() || freqToReactAbove == 0d) {// restoreOff
		if (this.getFridgeController().getFridge().switchOff()) {
		    logger.info(this.inputQueueName + " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+"
			    + this.getFridgeController().getRestoreDelay() + " - counter "
			    + this.getFridgeController().getCounterRestore());
		    this.getFridgeController().setRestoreToOff(false);
		    this.getFridgeController().initCounterRestore();
		}
	    }
	}
    }*/
}
