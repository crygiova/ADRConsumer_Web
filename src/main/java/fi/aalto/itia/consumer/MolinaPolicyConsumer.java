package fi.aalto.itia.consumer;

import java.io.IOException;
import java.util.Random;

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

public class MolinaPolicyConsumer extends ADRConsumer {

	/**
	 * 
	 */

	private static final long serialVersionUID = 5769062501178133949L;

	// 3 minutes is the maximum time for updating the status to the aggregator
	protected static final long MAX_TIME_FREQ_UPDATE = 5 * ADR_EM_Common.ONE_MIN;

	private Double[] freqToReactUnderArray = { 49.98, 49.97, 49.96, 49.95, 49.94, 49.93, 49.92, 49.91  };
	private Double[] freqToReactAboveArray = { 50.02, 50.03, 50.04, 50.05, 50.06, 50.07, 50.08, 50.09 };

	protected boolean lastInstructionUpdated;
	// aging parameter of the Consumer
	private AgingADRConsumer aging;
	private FCReactionDelay fcrd = new FCReactionDelay();

	protected InstructionsMessageContent lastInstruction;

	// Time of the last update sent to the aggregator
	private long lastUpdateSent;

	public MolinaPolicyConsumer(int ID, FridgeModel fridge) {
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
		// this.registerToAggregator(this.getCurrentUpdateMessageContentVersion2());
		Double freqToReactUnder = 0d;
		Double freqToReactAbove = 0d;
		Random dice5 = new Random();

		// Loop keep going
		while (this.keepGoing) {

			if (lastInstructionUpdated == true) {
				freqToReactUnder = lastInstruction.getUnderNominalFrequency();
				freqToReactAbove = lastInstruction.getAboveNominalFrequency();
				lastInstructionUpdated = false;
			}

			// XXX this is the control
			if (!this.getFridgeController().controlFridgeWithThresholds()) {
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

	private void applyFreqControlReactionDelayOriginal(Double freqToReactUnder, Double freqToReactAbove) {

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
			if (this.fcrd.getCounterRestore() > this.fcrd.getRestoreDelay() || freqToReactUnder == 0d) {
				if (this.getFridgeController().getFridge().switchOn()) {
					logger.info(this.inputQueueName + " *2*2*2*2*2*2**2*2*2*2*2*2*2*2*22*2* "
							+ this.fcrd.getRestoreDelay() + " - counter " + this.fcrd.getCounterRestore());
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
			if (this.fcrd.getCounterRestore() > this.fcrd.getRestoreDelay() || freqToReactAbove == 0d) {// restoreOff
				if (this.getFridgeController().getFridge().switchOff()) {
					logger.info(this.inputQueueName + " +2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2+2++2+2+"
							+ this.fcrd.getRestoreDelay() + " - counter " + this.fcrd.getCounterRestore());
					this.updateAggregator();
					this.getFridgeController().setRestoreToOff(false);
					this.fcrd.initCounterRestore();
				}
			}
		}
	}

	// generates update message content object with last updates ORIGINAL
	protected UpdateMessageContent getCurrentUpdateMessageContent() {
		// TODO missing the part where isCurrentOn but the status cannot be
		// changed for a while
		if (this.getFridgeController().getFridge().isCurrentOn())
		// && this.getFridgeController().getFridge().isPossibleToSwitchOff())
		{
			return SimulationMessageFactory.getUpdateMessageContent(
					this.getFridgeController().getFridge().getCurrentElectricPower(),
					this.getFridgeController().getFridge().getCurrentElectricPower(),
					this.getFridgeController().getFridge().getSecondsToTempMaxLimit(),
					this.getFridgeController().getFridge().getSecondsToTempMinLimit(), 0d, 0d, 0d, this.inputQueueName,
					this.aging);
		}
		/*
		 * if (!this.getFridgeController().getFridge().isCurrentOn() &&
		 * this.getFridgeController().getFridge().isPossibleToSwitchOn())
		 */else {
			return SimulationMessageFactory.getUpdateMessageContent(
					this.getFridgeController().getFridge().getCurrentElectricPower(),
					this.getFridgeController().getFridge().getCurrentElectricPower(), 0d, 0d,
					this.getFridgeController().getFridge().getPtcl(),
					this.getFridgeController().getFridge().getSecondsToTempMinLimit(),
					this.getFridgeController().getFridge().getSecondsToTempMaxLimit(), this.inputQueueName, this.aging);
		}
		// if it is ON or OFF but it is not possible to change the status
		/*
		 * return SimulationMessageFactory.getUpdateMessageContent(this.
		 * getFridgeController () .getFridge().getCurrentElectricPower(), 0d,
		 * 0d, 0d, 0d, 0d, 0d, this.inputQueueName, this.aging);
		 */
	}

	// second version with disconnected loads
	protected UpdateMessageContent getCurrentUpdateMessageContentVersion2() {
		// TODO missing the part where isCurrentOn but the status cannot be
		// changed for a while
		if (this.getFridgeController().getFridge().isCurrentOn()
				&& this.getFridgeController().getFridge().isPossibleToSwitchOff()) {
			// goes available
			if (!this.isAllocated()) {
				this.setMyState(ConsumerState.AVAILABLE_UNDER);
			}
			return SimulationMessageFactory.getUpdateMessageContent(
					this.getFridgeController().getFridge().getCurrentElectricPower(),
					this.getFridgeController().getFridge().getCurrentElectricPower(),
					this.getFridgeController().getFridge().getSecondsToTempMaxLimit(),
					this.getFridgeController().getFridge().getSecondsToTempMinLimit(), 0d, 0d, 0d, this.inputQueueName,
					this.aging);
		} else if (!this.getFridgeController().getFridge().isCurrentOn()
				&& this.getFridgeController().getFridge().isPossibleToSwitchOn()) {
			// goes available
			if (!this.isAllocated()) {
				this.setMyState(ConsumerState.AVAILABLE_OVER);
			}
			return SimulationMessageFactory.getUpdateMessageContent(
					this.getFridgeController().getFridge().getCurrentElectricPower(),
					this.getFridgeController().getFridge().getCurrentElectricPower(), 0d, 0d,
					this.getFridgeController().getFridge().getPtcl(),
					this.getFridgeController().getFridge().getSecondsToTempMinLimit(),
					this.getFridgeController().getFridge().getSecondsToTempMaxLimit(), this.inputQueueName, this.aging);
		}
		// if it is ON or OFF but it is not possible to change the status
		if (!this.isAllocated()) {
			this.setMyState(ConsumerState.IDLE);
		}
		return SimulationMessageFactory.getUpdateMessageContent(
				this.getFridgeController().getFridge().getCurrentElectricPower(), 0d, 0d, 0d, 0d, 0d, 0d,
				this.inputQueueName, this.aging);

	}

	protected void updateAggregator() {
		UpdateMessageContent umc = this.getCurrentUpdateMessageContent();
		// UpdateMessageContent umc =
		// this.getCurrentUpdateMessageContentVersion2();
		lastUpdateSent = System.currentTimeMillis();
		this.sendMessage(
				SimulationMessageFactory.getUpdateMessage(this.inputQueueName, ADR_EM_Common.AGG_INPUT_QUEUE, umc));
	}

	protected boolean registerToAggregator(UpdateMessageContent firstUpdate) {
		// send registration message
		// no delay
		this.sendMessage(SimulationMessageFactory.getRegisterMessage(this.inputQueueName, ADR_EM_Common.AGG_INPUT_QUEUE,
				firstUpdate), false);
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
			public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
					byte[] body) throws IOException {
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
