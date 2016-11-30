package fi.aalto.itia.consumer;

import java.io.IOException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.AgingADRConsumer;
import fi.aalto.itia.adr_em_common.FCReactionDelay;
import fi.aalto.itia.adr_em_common.InstructionsMessageContent;
import fi.aalto.itia.adr_em_common.SimulationMessage;
import fi.aalto.itia.adr_em_common.SimulationMessageFactory;
import fi.aalto.itia.adr_em_common.UpdateMessageContent;
import fi.aalto.itia.models.FridgeModel;
import fi.aalto.itia.util.Utility;

public class AggregatorADRConsumer extends ADRConsumer {

    /**
     * 
     */
    private static final long serialVersionUID = 5769062501178133949L;

    // 3 minutes is the maximum time for updating the status to the aggregator
    protected static final long MAX_TIME_FREQ_UPDATE = 3 * ADR_EM_Common.ONE_MIN;

    protected boolean lastInstructionUpdated;
    // aging parameter of the Consumer
    private AgingADRConsumer aging;
    private FCReactionDelay fcrd = new FCReactionDelay();

    protected InstructionsMessageContent lastInstruction;

    // Time of the last update sent to the aggregator
    private long lastUpdateSent;

    public AggregatorADRConsumer(int ID, FridgeModel fridge) {
	super(ID, fridge);
	this.lastInstruction = new InstructionsMessageContent(null);
	this.lastInstructionUpdated = false;
	// init aging
	this.aging = new AgingADRConsumer(this.inputQueueName, super.getID());
    }

    @Override
    public void run() {
	// Start consuming the AMQP queue
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
	    if (!this.getFridgeController().controlFridgeWithThresholds()) {
		// applyFreqControlReactionDelayOriginal(freqToReactUnder,
		// freqToReactAbove);
		applyFreqControlReactionDelay(freqToReactUnder, freqToReactAbove);
	    } else {
		// status changes means cannot do ADR
		this.fcrd.initCounterRestore();
		this.updateAggregator();
	    }
	    // check if we need to send a new update with last update sent
	    if (lastUpdateSent - System.currentTimeMillis() > MAX_TIME_FREQ_UPDATE) {
		this.updateAggregator();
	    }
	    try {
		// delay of the cycle
		//Thread.sleep(Math.round((2 + 3 * Utility.getRandom()) * ADR_EM_Common.ONE_SECOND));
		Thread.sleep(Math.round(ADR_EM_Common.ONE_SECOND));
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    /**
     * Similar to the V2 but in addition it has been added a reaction delay for
     * the consumers
     * 
     * @param freqToReactUnder
     * @param freqToReactAbove
     */
    private void applyFreqControlReactionDelay(Double freqToReactUnder, Double freqToReactAbove) {

	if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
		&& !this.getFridgeController().isRestoreToOn()) {
	    this.fcrd.addCounterReact();
	    // if it is time to react
	    if (this.fcrd.getCounterReact() > this.fcrd.getReactDelay()) {
		if (this.getFridgeController().reactDownFrequency()) {
		    // counting how many times it is reacting to Down frequency
		    this.aging.addReactDw();
		} else {
		    // update the aggregator
		    this.updateAggregator();
		}
		this.fcrd.initCounterReact();
	    }
	    this.fcrd.initCounterRestore();
	} else if (this.getFridgeController().isRestoreToOn()) {
	    if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
		this.fcrd.initCounterRestore();
	    } else {
		this.fcrd.addCounterRestore();
	    }
	    // restoreON
	    if (this.fcrd.getCounterRestore() > this.fcrd.getRestoreDelay())
	    // || freqToReactUnder == 0d)
	    {
		if (this.getFridgeController().restoreDownFrequency()) {
		    this.fcrd.initCounterRestore();
		    this.updateAggregator();
		}
	    }
	}
	// 2//
	if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
		&& !this.getFridgeController().isRestoreToOff()) {
	    this.fcrd.addCounterReact();
	    // if it is time to react
	    if (this.fcrd.getCounterReact() > this.fcrd.getReactDelay()) {
		// Ask the frdge to execute the reaction
		if (this.getFridgeController().reactUpFrequency()) {
		    // counting how many times it is reacting to up frequency
		    this.aging.addReactUp();
		} else {
		    this.updateAggregator();
		}
		this.fcrd.initCounterReact();
	    }
	    this.fcrd.initCounterRestore();
	} else if (this.getFridgeController().isRestoreToOff()) {
	    if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.fcrd.initCounterRestore();
	    } else {
		this.fcrd.addCounterRestore();
	    }
	    if (this.fcrd.getCounterRestore() > this.fcrd.getRestoreDelay())
	    // || freqToReactAbove == 0d)
	    {// restoreOff
		if (this.getFridgeController().restoreUpFrequency()) {
		    this.fcrd.initCounterRestore();
		    this.updateAggregator();
		}
	    }
	}
    }

    private void applyFreqControlReactionDelayOriginal(Double freqToReactUnder,
	    Double freqToReactAbove) {

	if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
		&& !this.getFridgeController().isRestoreToOn()) {
	    this.fcrd.addCounterReact(); // if it is time to react
	    if (this.fcrd.getCounterReact() > this.fcrd.getReactDelay()) {

		if (this.getFridgeController().getFridge().isCurrentOn()
			&& this.getFridgeController().getFridge().isPossibleToSwitchOff()) {
		    this.getFridgeController().getFridge().switchOff();
		    this.getFridgeController().setRestoreToOn(true);
		    // counting
		    // how many
		    // times it is reacting to Down frequency
		    // this.aging.addReactDw();
		} else {
		    // if cannot react
		    this.updateAggregator();
		}

		this.fcrd.initCounterReact();
	    }
	    this.fcrd.initCounterRestore();
	} else if (this.getFridgeController().isRestoreToOn()) {
	    if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
		this.fcrd.initCounterRestore();
	    } else {
		this.fcrd.addCounterRestore();
	    } // restoreON
	    if (this.fcrd.getCounterRestore() > this.fcrd.getRestoreDelay()
		    || freqToReactUnder == 0d) {
		if (this.getFridgeController().getFridge().switchOn()) {
		    logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
			    + this.fcrd.getRestoreDelay() + " - counter "
			    + this.fcrd.getCounterRestore());
		    this.updateAggregator();
		    this.getFridgeController().setRestoreToOn(false);
		    this.fcrd.initCounterRestore();
		}
	    }
	}

	// 2//
	if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
		&& !this.getFridgeController().isRestoreToOff()) {
	    this.fcrd.addCounterReact(); // if it is time tos react
	    if (this.fcrd.getCounterReact() > this.fcrd.getReactDelay()) {
		if (!this.getFridgeController().getFridge().isCurrentOn()
			&& this.getFridgeController().getFridge().isPossibleToSwitchOn()
			&& !this.getFridgeController().isRestoreToOff()) {
		    this.getFridgeController().getFridge().switchOn();
		    this.getFridgeController().setRestoreToOff(true);
		    // counting how many times it is reacting to up frequency
		    this.aging.addReactUp();
		} else {
		    this.updateAggregator();
		}
		this.fcrd.initCounterReact();
	    }
	    this.fcrd.initCounterRestore();
	} else if (this.getFridgeController().isRestoreToOff()) {
	    if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.fcrd.initCounterRestore();
	    } else {
		this.fcrd.addCounterRestore();
	    }
	    if (this.fcrd.getCounterRestore() > this.fcrd.getRestoreDelay()
		    || freqToReactAbove == 0d) {// restoreOff
		if (this.getFridgeController().getFridge().switchOff()) {
		    logger.info(this.inputQueueName + " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+"
			    + this.fcrd.getRestoreDelay() + " - counter "
			    + this.fcrd.getCounterRestore());
		    this.updateAggregator();
		    this.getFridgeController().setRestoreToOff(false);
		    this.fcrd.initCounterRestore();
		}
	    }
	}
    }

    // generates update message content object with last updates
    protected UpdateMessageContent getCurrentUpdateMessageContent() {
	// TODO missing the part where isCurrentOn but the status cannot be
	// changed for a while
	if (this.getFridgeController().getFridge().isCurrentOn())
	// && this.getFridgeController().getFridge().isPossibleToSwitchOff())
	{
	    return SimulationMessageFactory.getUpdateMessageContent(this.getFridgeController()
		    .getFridge().getCurrentElectricPower(), this.getFridgeController().getFridge()
		    .getCurrentElectricPower(), this.getFridgeController().getFridge()
		    .getSecondsToTempMaxLimit(), this.getFridgeController().getFridge()
		    .getSecondsToTempMinLimit(), 0d, 0d, 0d, this.inputQueueName, this.aging);
	}
	/*
	 * if (!this.getFridgeController().getFridge().isCurrentOn() &&
	 * this.getFridgeController().getFridge().isPossibleToSwitchOn())
	 */else {
	    return SimulationMessageFactory.getUpdateMessageContent(this.getFridgeController()
		    .getFridge().getCurrentElectricPower(), this.getFridgeController().getFridge()
		    .getCurrentElectricPower(), 0d, 0d, this.getFridgeController().getFridge()
		    .getPtcl(), this.getFridgeController().getFridge().getSecondsToTempMinLimit(),
		    this.getFridgeController().getFridge().getSecondsToTempMaxLimit(),
		    this.inputQueueName, this.aging);
	}
	// if it is ON or OFF but it is not possible to change the status
	/*
	 * return
	 * SimulationMessageFactory.getUpdateMessageContent(this.getFridgeController
	 * () .getFridge().getCurrentElectricPower(), 0d, 0d, 0d, 0d, 0d, 0d,
	 * this.inputQueueName, this.aging);
	 */
    }

    protected void updateAggregator() {
	UpdateMessageContent umc = this.getCurrentUpdateMessageContent();
	lastUpdateSent = System.currentTimeMillis();
	this.sendMessage(SimulationMessageFactory.getUpdateMessage(this.inputQueueName,
		ADR_EM_Common.AGG_INPUT_QUEUE, umc));
    }

    protected boolean registerToAggregator(UpdateMessageContent firstUpdate) {
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

    public AgingADRConsumer getAging() {
	return aging;
    }

    public void setAging(AgingADRConsumer aging) {
	this.aging = aging;
    }

    /**
     * slower reaction *
     * 
     * @Deprecated
     * @Autowired private void applyFreqControl(Double freqToReactUnder, Double
     *            freqToReactAbove) { // TODO missing the restore back if
     *            (freqToReactUnder != 0d || this.isRestoreToOn()) { // if the
     *            frequency is lower than my react freq if
     *            (FrequencyReader.getCurrentFreqValue() <= freqToReactUnder &&
     *            !this.isRestoreToOn()) { // reactDelay increase
     *            this.addCounterReact(); if (this.getCounterReact() >
     *            this.reactDelay) { if (this.getFridge().isCurrentOn() &&
     *            this.getFridge().isPossibleToSwitchOff()) {
     *            this.getFridge().switchOff(); this.setRestoreToOn(true); //
     *            counting how many times it is reacting to Down // frequency
     *            this.aging.addReactDw(); } this.initCounterReact(); }
     *            this.initCounterRestore(); } else if (this.isRestoreToOn()) {
     *            // TODO TODO this part is to test RESTORE
     *            this.addCounterRestore(); if (this.getCounterRestore() >
     *            restoreDelay) { logger.info(this.inputQueueName +
     *            " ****************** " + this.restoreDelay + " - counter " +
     *            this.getCounterRestore()); this.updateAggregator();
     *            this.setRestoreToOn(false); this.getFridge().switchOn();
     *            this.initCounterRestore(); } } } if (freqToReactAbove != 0d ||
     *            this.isRestoreToOff()) { if
     *            (FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
     *            this.addCounterReact(); // if it is time to react if
     *            (this.getCounterReact() > this.reactDelay) { if
     *            (!this.getFridge().isCurrentOn() &&
     *            this.getFridge().isPossibleToSwitchOn() &&
     *            !this.isRestoreToOff()) { this.getFridge().switchOn();
     *            this.setRestoreToOff(true); // counting how many times it is
     *            reacting to up // frequency this.aging.addReactUp(); }
     *            this.initCounterReact(); } this.initCounterRestore(); } else
     *            if (this.isRestoreToOff()) { // TODO TODO this part is to test
     * 
     *            this.addCounterRestore(); if (this.getCounterRestore() >
     *            restoreDelay) { logger.info(this.inputQueueName +
     *            " ++++++++++++++++++++++ " + this.restoreDelay + " - counter "
     *            + this.getCounterRestore()); this.updateAggregator();
     *            this.setRestoreToOff(false); this.getFridge().switchOff();
     *            this.initCounterRestore(); } } } }
     * 
     *            /** This version is the one utilized for the FCR-N control
     *            (2nd conf paper)
     * 
     * @param freqToReactUnder
     * @param freqToReactAbove
     *
     *            private void applyFreqControlV2(Double freqToReactUnder,
     *            Double freqToReactAbove) {
     * 
     *            if (freqToReactUnder != 0d &&
     *            FrequencyReader.getCurrentFreqValue() <= freqToReactUnder &&
     *            !this.isRestoreToOn()) { // this.addCounterReact(); // // if
     *            it is time to react // if (this.getCounterReact() >
     *            this.reactDelay) { if (this.getFridge().isCurrentOn() &&
     *            this.getFridge().isPossibleToSwitchOff()) {
     *            this.getFridge().switchOff(); this.setRestoreToOn(true); //
     *            counting how many times it is reacting to Down frequency
     *            this.aging.addReactDw(); } else { // if cannot react
     *            this.updateAggregator(); } // this.initCounterReact(); // }
     *            this.initCounterRestore(); } else if (this.isRestoreToOn()) {
     *            if (freqToReactUnder != 0d &&
     *            FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
     *            this.initCounterRestore(); } else { this.addCounterRestore();
     *            } if (this.getCounterRestore() > restoreDelay ||
     *            freqToReactUnder == 0d) { // restoreOn
     *            logger.info(this.inputQueueName +
     *            " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* " + this.restoreDelay +
     *            " - counter " + this.getCounterRestore());
     *            this.updateAggregator(); this.setRestoreToOn(false);
     *            this.getFridge().switchOn(); this.initCounterRestore(); } }
     * 
     *            // 2// if (freqToReactAbove != 0d &&
     *            FrequencyReader.getCurrentFreqValue() >= freqToReactAbove &&
     *            !this.isRestoreToOff()) { // this.addCounterReact(); // // if
     *            it is time to react // if (this.getCounterReact() >
     *            this.reactDelay) { if (!this.getFridge().isCurrentOn() &&
     *            this.getFridge().isPossibleToSwitchOn() &&
     *            !this.isRestoreToOff()) { this.getFridge().switchOn();
     *            this.setRestoreToOff(true); // counting how many times it is
     *            reacting to up frequency this.aging.addReactUp(); } else {
     *            this.updateAggregator(); } // this.initCounterReact(); // }
     *            this.initCounterRestore(); } else if (this.isRestoreToOff()) {
     *            if (freqToReactAbove != 0d &&
     *            FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
     *            this.initCounterRestore(); } else { this.addCounterRestore();
     *            } if (this.getCounterRestore() > restoreDelay ||
     *            freqToReactAbove == 0d) {// restoreOff
     *            logger.info(this.inputQueueName +
     *            " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+" +
     *            this.restoreDelay + " - counter " + this.getCounterRestore());
     *            this.updateAggregator(); this.setRestoreToOff(false);
     *            this.getFridge().switchOff(); this.initCounterRestore(); } } }
     */
}
