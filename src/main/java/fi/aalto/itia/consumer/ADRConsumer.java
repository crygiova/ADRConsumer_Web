package fi.aalto.itia.consumer;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.AgingADRConsumer;
import fi.aalto.itia.adr_em_common.InstructionsMessageContent;
import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.adr_em_common.SimulationMessage;
import fi.aalto.itia.adr_em_common.SimulationMessageFactory;
import fi.aalto.itia.adr_em_common.UpdateMessageContent;
import fi.aalto.itia.models.FridgeModel;
import fi.aalto.itia.util.Utility;

import org.apache.commons.math3.distribution.BetaDistribution;
import org.apache.commons.math3.distribution.LevyDistribution;

public class ADRConsumer extends SimulationElement {
    /**
	 * 
	 */
    private static final Logger logger = LoggerFactory.getLogger(ADRConsumer.class);
    private static final long serialVersionUID = 4328592954437775437L;
    private static final String PREFIX_INPUT_QUEUE = "adrc_";
    private static final long MAX_TIME_FREQ_UPDATE = 3 * ADR_EM_Common.ONE_MIN;
    private static final int RESTORE_DELAY_CONST = 0;
    private static final int RESTORE_DELAY_VAR = 30;

    private FridgeModel fridge;
    private final int ID;
    private boolean lastInstructionUpdated;

    private AgingADRConsumer aging;

    private InstructionsMessageContent lastInstruction;
    private boolean restoreToOn = false;
    private int counterRestore = 0;
    private boolean restoreToOff = false;
    // from:
    // http://commons.apache.org/proper/commons-math/userguide/distribution.html
    private BetaDistribution betaD = new BetaDistribution(3, 7);
    // XXX important since is the restore delay
    private int restoreDelay;
    // Time of the last update sent to the aggregator
    private long lastUpdateSent;

    public ADRConsumer(int ID, FridgeModel fridge) {
	super(PREFIX_INPUT_QUEUE + ID);
	this.ID = ID;
	this.fridge = fridge;
	this.lastInstruction = new InstructionsMessageContent(null);
	this.lastInstructionUpdated = false;
	// random generated delay to restore from DR action
	this.restoreDelay = (int) (RESTORE_DELAY_CONST + Math.round(RESTORE_DELAY_VAR
		* betaD.sample()));
	// init aging
	this.aging = new AgingADRConsumer(this.inputQueueName, this.ID);
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
    @Override
    public void run() {
	this.startConsumingMq();
	// Random msg based on fridge on or off
	this.registerToAggregator(this.getCurrentUpdateMessageContent());

	Double freqToReactUnder = 0d;

	Double freqToReactAbove = 0d;

	// Loop keep going
	while (this.keepGoing) {
	    if (lastInstructionUpdated == true) {
		freqToReactUnder = lastInstruction.getUnderNominalFrequency();
		freqToReactAbove = lastInstruction.getAboveNominalFrequency();
		lastInstructionUpdated = false;
	    }
	    // XXX this is the control
	    if (!this.controlFridgeWithThresholds()) {
		// applyFreqControl(freqToReactUnder, freqToReactAbove);
		applyFreqControlV2(freqToReactUnder, freqToReactAbove);
	    } else {
		// NB this should be changed
		this.updateAggregator();
	    }
	    // check if we need to send a new update with last update sent
	    if (lastUpdateSent - System.currentTimeMillis() > MAX_TIME_FREQ_UPDATE) {
		this.updateAggregator();
	    }
	    try {
		Thread.sleep(Math.round((2 + 3 * Utility.getRandom()) * ADR_EM_Common.ONE_SECOND));
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    private void applyFreqControl(Double freqToReactUnder, Double freqToReactAbove) {
	// TODO missing the restore back
	if (freqToReactUnder != 0d || this.isRestoreToOn()) {
	    // if the frequency is lower than my react freq
	    if (FrequencyReader.getCurrentFreqValue() <= freqToReactUnder && !this.isRestoreToOn()) {
		if (this.fridge.isCurrentOn() && this.fridge.isPossibleToSwitchOff()) {
		    this.fridge.switchOff();
		    this.setRestoreToOn(true);
		    // counting how many times it is reacting to Down frequency
		    this.aging.addReactDw();
		}
		this.initCounterRestore();
	    } else if (this.isRestoreToOn()) {
		// TODO TODO this part is to test RESTORE
		this.addCounterRestore();
		if (counterRestore > restoreDelay) {
		    logger.info(this.inputQueueName + " ****************** " + this.restoreDelay
			    + " - counter " + counterRestore);
		    this.updateAggregator();
		    this.setRestoreToOn(false);
		    this.fridge.switchOn();
		    this.initCounterRestore();
		}
	    }
	}
	if (freqToReactAbove != 0d || this.isRestoreToOff()) {
	    if (FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		if (!this.fridge.isCurrentOn() && this.fridge.isPossibleToSwitchOn()
			&& !this.isRestoreToOff()) {
		    this.fridge.switchOn();
		    this.setRestoreToOff(true);
		    // counting how many times it is reacting to up frequency
		    this.aging.addReactUp();
		}
		this.initCounterRestore();
	    } else if (this.isRestoreToOff()) {
		// TODO TODO this part is to test

		this.addCounterRestore();
		if (counterRestore > restoreDelay) {
		    logger.info(this.inputQueueName + " ++++++++++++++++++++++ "
			    + this.restoreDelay + " - counter " + counterRestore);
		    this.updateAggregator();
		    this.setRestoreToOff(false);
		    this.fridge.switchOff();
		    this.initCounterRestore();
		}
	    }
	}
    }

    private void applyFreqControlV2(Double freqToReactUnder, Double freqToReactAbove) {

	if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
		&& !this.isRestoreToOn()) {
	    if (this.fridge.isCurrentOn() && this.fridge.isPossibleToSwitchOff()) {
		this.fridge.switchOff();
		this.setRestoreToOn(true);
		// counting how many times it is reacting to Down frequency
		this.aging.addReactDw();
	    } else {
		// if cannot react
		this.updateAggregator();
	    }
	    this.initCounterRestore();
	} else if (this.isRestoreToOn()) {
	    if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
		this.initCounterRestore();
	    } else {
		this.addCounterRestore();
	    }
	    if (counterRestore > restoreDelay || freqToReactUnder == 0d) { // restoreOn
		logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
			+ this.restoreDelay + " - counter " + counterRestore);
		this.updateAggregator();
		this.setRestoreToOn(false);
		this.fridge.switchOn();
		this.initCounterRestore();
	    }
	}

	// 2//
	if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
		&& !this.isRestoreToOff()) {
	    if (!this.fridge.isCurrentOn() && this.fridge.isPossibleToSwitchOn()
		    && !this.isRestoreToOff()) {
		this.fridge.switchOn();
		this.setRestoreToOff(true);
		// counting how many times it is reacting to up frequency
		this.aging.addReactUp();
	    } else {
		this.updateAggregator();
	    }
	    this.initCounterRestore();
	} else if (this.isRestoreToOff()) {
	    if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.initCounterRestore();
	    } else {
		this.addCounterRestore();
	    }
	    if (counterRestore > restoreDelay || freqToReactAbove == 0d) {// restoreOff
		logger.info(this.inputQueueName + " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+"
			+ this.restoreDelay + " - counter " + counterRestore);
		this.updateAggregator();
		this.setRestoreToOff(false);
		this.fridge.switchOff();
		this.initCounterRestore();
	    }
	}
    }

    private boolean controlFridgeWithThresholds() {
	// CONTROL Of the FRIDGE
	// T > Tmax and notOn
	if (this.fridge.getCurrentTemperature() > (this.fridge.getTemperatureSP() + this.fridge
		.getThermoBandDT()) && !this.fridge.isCurrentOn()) {
	    this.fridge.switchOn();
	    this.setRestoreToOn(false);
	    this.initCounterRestore();
	    return true;
	}
	// T < Tmin and On
	if (this.fridge.getCurrentTemperature() < (this.fridge.getTemperatureSP() - this.fridge
		.getThermoBandDT()) && this.fridge.isCurrentOn()) {
	    this.fridge.switchOff();
	    this.setRestoreToOff(false);
	    this.initCounterRestore();
	    return true;
	}
	return false;
    }

    // generates update message content object with last updates
    private UpdateMessageContent getCurrentUpdateMessageContent() {
	if (this.fridge.isCurrentOn()) {
	    return SimulationMessageFactory.getUpdateMessageContent(
		    this.fridge.getCurrentElectricPower(), this.fridge.getCurrentElectricPower(),
		    this.fridge.getSecondsToTempMaxLimit(), this.fridge.getSecondsToTempMinLimit(),
		    0d, 0d, 0d, this.inputQueueName, this.aging);
	} else {
	    return SimulationMessageFactory.getUpdateMessageContent(
		    this.fridge.getCurrentElectricPower(), this.fridge.getCurrentElectricPower(),
		    0d, 0d, this.fridge.getPtcl(), this.fridge.getSecondsToTempMinLimit(),
		    this.fridge.getSecondsToTempMaxLimit(), this.inputQueueName, this.aging);
	}
    }

    private void updateAggregator() {
	// TODO Auto-generated method stub
	UpdateMessageContent umc = this.getCurrentUpdateMessageContent();
	lastUpdateSent = System.currentTimeMillis();
	this.sendMessage(SimulationMessageFactory.getUpdateMessage(this.inputQueueName,
		ADR_EM_Common.AGG_INPUT_QUEUE, umc));
    }

    private boolean registerToAggregator(UpdateMessageContent firstUpdate) {
	// send registration message
	// TODO change
	this.sendMessage(SimulationMessageFactory.getRegisterMessage(this.inputQueueName,
		ADR_EM_Common.AGG_INPUT_QUEUE, firstUpdate));
	SimulationMessage reg = this.waitForMessage();
	lastUpdateSent = System.currentTimeMillis();
	if (reg.getHeader().compareTo(ADR_EM_Common.ACCEPT_REG_HEADER) == 0) {
	    return true;
	}
	// if (reg.getHeader().compareTo(ADR_EM_Common.DENY_REG_HEADER) == 0) {
	else {
	    this.setKeepGoing(false);
	    return false;
	}
    }

    public void startConsumingMq() {
	Consumer consumer = new DefaultConsumer(dRChannel) {
	    @Override
	    public void handleDelivery(String consumerTag, Envelope envelope,
		    AMQP.BasicProperties properties, byte[] body) throws IOException {
		SimulationMessage sm = null;
		try {
		    sm = (SimulationMessage) SimulationMessage.deserialize(body);
		} catch (ClassNotFoundException e) {
		    e.printStackTrace();
		}
		if (sm != null) {
		    routeInputMessage(sm);
		}
	    }
	};
	try {
	    dRChannel.basicConsume(inputQueueName, true, consumer);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    // This function routes the input messages based on their headers
    public void routeInputMessage(SimulationMessage sm) {
	switch (sm.getHeader()) {
	case ADR_EM_Common.INSTRUCTIONS_HEADER:
	    if (sm.getContent() instanceof InstructionsMessageContent) {
		this.lastInstruction = (InstructionsMessageContent) sm.getContent();
		this.lastInstructionUpdated = true;
	    }
	    break;
	// case ADR_EM_Common.ACCEPT_REG_HEADER:
	// // add the consumer to the set
	// addMessage(sm);
	// break;
	// case ADR_EM_Common.DENY_REG_HEADER:
	// addMessage(sm);
	// break;
	default:
	    addMessage(sm);
	    break;
	}
    }

    public void addCounterRestore() {
	counterRestore++;
    }

    public void initCounterRestore() {
	counterRestore = 0;
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

    public int getCounterRestoreMax() {
	return restoreDelay;
    }

    public void setCounterRestoreMax(int counterRestoreMax) {
	this.restoreDelay = counterRestoreMax;
    }

    public AgingADRConsumer getAging() {
	return aging;
    }

    public void setAging(AgingADRConsumer aging) {
	this.aging = aging;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Runnable#run()
     */
    /*
     * @Override public void run() { this.startConsumingMq(); //FridgeON
     * this.fridge.switchOn();
     * this.registerToAggregator(SimulationMessageFactory
     * .getSemiRandomUpdateMessage(this.inputQueueName)); // TODO wait for the
     * message SimulationMessage sm = this.waitForMessage(); Double freqToReact
     * = ((InstructionsMessageContent) sm.getContent())
     * .getUnderNominalFrequency(); // Loop keep going while (this.keepGoing) {
     * if (FrequencyReader.getCurrentFreqValue() <= freqToReact) {
     * this.fridge.switchOff(); } try { Thread.sleep(3 * 1000); } catch
     * (InterruptedException e) { // TODO Auto-generated catch block
     * e.printStackTrace(); } } // Random rand = new Random(); // while
     * (this.keepGoing) { // int msecUpdate = 5 * 60000; // // try { //
     * Thread.sleep(1 + msecUpdate // - (Math.round(msecUpdate *
     * rand.nextFloat()))); // } catch (InterruptedException e) { //
     * e.printStackTrace(); // } // // Random Control //
     * this.fridge.switchOnOff(rand.nextBoolean()); // } }
     */
    // TODO This function should generate and update message for the aggregator
    // and send it
}
