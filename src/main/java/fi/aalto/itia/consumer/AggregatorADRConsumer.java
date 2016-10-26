package fi.aalto.itia.consumer;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.AgingADRConsumer;
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

    protected boolean lastInstructionUpdated;
    // aging parameter of the Consumer
    private AgingADRConsumer aging;

    protected InstructionsMessageContent lastInstruction;

    // Time of the last update sent to the aggregator
    private long lastUpdateSent;

    public AggregatorADRConsumer(int ID, FridgeModel fridge) {
	super(ID, fridge);
	// TODO Auto-generated constructor stub
	this.lastInstruction = new InstructionsMessageContent(null);
	this.lastInstructionUpdated = false;
	// init aging
	this.aging = new AgingADRConsumer(this.inputQueueName, super.getID());
    }

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
		// delay of the cycle
		Thread.sleep(Math.round((2 + 3 * Utility.getRandom()) * ADR_EM_Common.ONE_SECOND));
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	}
    }

    /**
     * slower reaction
     * */
    @Autowired
    private void applyFreqControl(Double freqToReactUnder, Double freqToReactAbove) {
	// TODO missing the restore back
	if (freqToReactUnder != 0d || this.isRestoreToOn()) {
	    // if the frequency is lower than my react freq
	    if (FrequencyReader.getCurrentFreqValue() <= freqToReactUnder && !this.isRestoreToOn()) {
		// reactDelay increase
		this.addCounterReact();
		if (this.getCounterReact() > this.reactDelay) {
		    if (this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOff()) {
			this.getFridge().switchOff();
			this.setRestoreToOn(true);
			// counting how many times it is reacting to Down
			// frequency
			this.aging.addReactDw();
		    }
		    this.initCounterReact();
		}
		this.initCounterRestore();
	    } else if (this.isRestoreToOn()) {
		// TODO TODO this part is to test RESTORE
		this.addCounterRestore();
		if (this.getCounterRestore() > restoreDelay) {
		    logger.info(this.inputQueueName + " ****************** " + this.restoreDelay
			    + " - counter " + this.getCounterRestore());
		    this.updateAggregator();
		    this.setRestoreToOn(false);
		    this.getFridge().switchOn();
		    this.initCounterRestore();
		}
	    }
	}
	if (freqToReactAbove != 0d || this.isRestoreToOff()) {
	    if (FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.addCounterReact();
		// if it is time to react
		if (this.getCounterReact() > this.reactDelay) {
		    if (!this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOn()
			    && !this.isRestoreToOff()) {
			this.getFridge().switchOn();
			this.setRestoreToOff(true);
			// counting how many times it is reacting to up
			// frequency
			this.aging.addReactUp();
		    }
		    this.initCounterReact();
		}
		this.initCounterRestore();
	    } else if (this.isRestoreToOff()) {
		// TODO TODO this part is to test

		this.addCounterRestore();
		if (this.getCounterRestore() > restoreDelay) {
		    logger.info(this.inputQueueName + " ++++++++++++++++++++++ "
			    + this.restoreDelay + " - counter " + this.getCounterRestore());
		    this.updateAggregator();
		    this.setRestoreToOff(false);
		    this.getFridge().switchOff();
		    this.initCounterRestore();
		}
	    }
	}
    }

    private void applyFreqControlV2(Double freqToReactUnder, Double freqToReactAbove) {

	if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
		&& !this.isRestoreToOn()) {
//	    this.addCounterReact();
//	    // if it is time to react
//	    if (this.getCounterReact() > this.reactDelay) {
		if (this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOff()) {
		    this.getFridge().switchOff();
		    this.setRestoreToOn(true);
		    // counting how many times it is reacting to Down frequency
		    this.aging.addReactDw();
		} else {
		    // if cannot react
		    this.updateAggregator();
		}
	    // this.initCounterReact();
	    // }
	    this.initCounterRestore();
	} else if (this.isRestoreToOn()) {
	    if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
		this.initCounterRestore();
	    } else {
		this.addCounterRestore();
	    }
	    if (this.getCounterRestore() > restoreDelay || freqToReactUnder == 0d) { // restoreOn
		logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
			+ this.restoreDelay + " - counter " + this.getCounterRestore());
		this.updateAggregator();
		this.setRestoreToOn(false);
		this.getFridge().switchOn();
		this.initCounterRestore();
	    }
	}

	// 2//
	if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
		&& !this.isRestoreToOff()) {
//	    this.addCounterReact();
//	    // if it is time to react
//	    if (this.getCounterReact() > this.reactDelay) {
		if (!this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOn()
			&& !this.isRestoreToOff()) {
		    this.getFridge().switchOn();
		    this.setRestoreToOff(true);
		    // counting how many times it is reacting to up frequency
		    this.aging.addReactUp();
		} else {
		    this.updateAggregator();
		}
//		this.initCounterReact();
//	    }
	    this.initCounterRestore();
	} else if (this.isRestoreToOff()) {
	    if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.initCounterRestore();
	    } else {
		this.addCounterRestore();
	    }
	    if (this.getCounterRestore() > restoreDelay || freqToReactAbove == 0d) {// restoreOff
		logger.info(this.inputQueueName + " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+"
			+ this.restoreDelay + " - counter " + this.getCounterRestore());
		this.updateAggregator();
		this.setRestoreToOff(false);
		this.getFridge().switchOff();
		this.initCounterRestore();
	    }
	}
    }
    
    //added delay of the consumers in the reaction
    private void applyFreqControlV3(Double freqToReactUnder, Double freqToReactAbove) {

	if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
		&& !this.isRestoreToOn()) {
	    this.addCounterReact();
	    // if it is time to react
	    if (this.getCounterReact() > this.reactDelay) {
		if (this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOff()) {
		    this.getFridge().switchOff();
		    this.setRestoreToOn(true);
		    // counting how many times it is reacting to Down frequency
		    this.aging.addReactDw();
		} else {
		    // if cannot react
		    this.updateAggregator();
		}
		this.initCounterReact();
	    }
	    this.initCounterRestore();
	} else if (this.isRestoreToOn()) {
	    if (freqToReactUnder != 0d && FrequencyReader.getCurrentFreqValue() <= freqToReactUnder) {
		this.initCounterRestore();
	    } else {
		this.addCounterRestore();
	    }
	    if (this.getCounterRestore() > restoreDelay || freqToReactUnder == 0d) { // restoreOn
		logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
			+ this.restoreDelay + " - counter " + this.getCounterRestore());
		this.updateAggregator();
		this.setRestoreToOn(false);
		this.getFridge().switchOn();
		this.initCounterRestore();
	    }
	}

	// 2//
	if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
		&& !this.isRestoreToOff()) {
	    this.addCounterReact();
	    // if it is time to react
	    if (this.getCounterReact() > this.reactDelay) {
		if (!this.getFridge().isCurrentOn() && this.getFridge().isPossibleToSwitchOn()
			&& !this.isRestoreToOff()) {
		    this.getFridge().switchOn();
		    this.setRestoreToOff(true);
		    // counting how many times it is reacting to up frequency
		    this.aging.addReactUp();
		} else {
		    this.updateAggregator();
		}
		this.initCounterReact();
	    }
	    this.initCounterRestore();
	} else if (this.isRestoreToOff()) {
	    if (freqToReactAbove != 0d && FrequencyReader.getCurrentFreqValue() >= freqToReactAbove) {
		this.initCounterRestore();
	    } else {
		this.addCounterRestore();
	    }
	    if (this.getCounterRestore() > restoreDelay || freqToReactAbove == 0d) {// restoreOff
		logger.info(this.inputQueueName + " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+"
			+ this.restoreDelay + " - counter " + this.getCounterRestore());
		this.updateAggregator();
		this.setRestoreToOff(false);
		this.getFridge().switchOff();
		this.initCounterRestore();
	    }
	}
    }

    // generates update message content object with last updates
    protected UpdateMessageContent getCurrentUpdateMessageContent() {
	if (this.getFridge().isCurrentOn()) {
	    return SimulationMessageFactory.getUpdateMessageContent(this.getFridge()
		    .getCurrentElectricPower(), this.getFridge().getCurrentElectricPower(), this
		    .getFridge().getSecondsToTempMaxLimit(), this.getFridge()
		    .getSecondsToTempMinLimit(), 0d, 0d, 0d, this.inputQueueName, this.aging);
	} else {
	    return SimulationMessageFactory.getUpdateMessageContent(this.getFridge()
		    .getCurrentElectricPower(), this.getFridge().getCurrentElectricPower(), 0d, 0d,
		    this.getFridge().getPtcl(), this.getFridge().getSecondsToTempMinLimit(), this
			    .getFridge().getSecondsToTempMaxLimit(), this.inputQueueName,
		    this.aging);
	}
    }

    protected void updateAggregator() {
	// TODO Auto-generated method stub
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
}
