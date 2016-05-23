package fi.aalto.itia.consumer;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.InstructionsMessageContent;
import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.adr_em_common.SimulationMessage;
import fi.aalto.itia.adr_em_common.SimulationMessageFactory;
import fi.aalto.itia.adr_em_common.UpdateMessageContent;
import fi.aalto.itia.models.FridgeModel;
import fi.aalto.itia.util.Utility;

public class ADRConsumer extends SimulationElement {
	/**
	 * 
	 */
	private static final Logger logger = LoggerFactory
			.getLogger(ADRConsumer.class);
	private static final long serialVersionUID = 4328592954437775437L;
	private static final String PREFIX_INPUT_QUEUE = "adrc_";
	private FridgeModel fridge;
	private final int ID;
	private boolean lastInstructionUpdated;

	private InstructionsMessageContent lastInstruction;
	private boolean restoreToOn = false;
	private int counterRestore = 0;
	private boolean restoreToOff = false;
	private int counterRestoreMax = (int) Math.round(1 + 5 * Utility
			.getRandom());

	public ADRConsumer(int ID, FridgeModel fridge) {
		super(PREFIX_INPUT_QUEUE + ID);
		this.ID = ID;
		this.fridge = fridge;
		this.lastInstruction = new InstructionsMessageContent(null);
		this.lastInstructionUpdated = false;
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
		this.registerToAggregator(SimulationMessageFactory
				.getSemiRandomUpdateMessage(this.inputQueueName,
						this.fridge.isCurrentOn()));
		// TODO wait for the FIRST INSTRUCTION message
		// TODO last instruction
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
			if (!this.controlFridgeWithThresholds())
				applyFreqControl(freqToReactUnder, freqToReactAbove);
			else {
				this.updateAggregator();
			}
			try {
				Thread.sleep(Math.round((2 + 3 * Utility.getRandom()) * 1000));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private void applyFreqControl(Double freqToReactUnder,
			Double freqToReactAbove) {
		// TODO missing the restore back
		if (freqToReactUnder != 0d) {
			if (FrequencyReader.getCurrentFreqValue() <= freqToReactUnder
					&& this.fridge.isCurrentOn()) {
				this.fridge.switchOff();
				this.setRestoreToOn(true);
			} else if (this.isRestoreToOn()) {
				// TODO TODO this part is to test RESTORE
				// this.addCounterRestore();
				// if (counterRestore > counterRestoreMax) {
				this.setRestoreToOn(false);
				this.fridge.switchOn();
				// this.initCounterRestore();
				// }
			}

		}
		if (freqToReactAbove != 0d) {
			if (FrequencyReader.getCurrentFreqValue() >= freqToReactAbove
					&& !this.fridge.isCurrentOn()) {
				this.fridge.switchOn();
				this.setRestoreToOff(true);
			} else if (this.isRestoreToOff()) {
				// TODO TODO this part is to test
				// this.addCounterRestore();
				// if (counterRestore > counterRestoreMax) {
				this.setRestoreToOff(false);
				this.fridge.switchOff();
				// this.initCounterRestore();
				// }
			}

		}
	}

	private boolean controlFridgeWithThresholds() {
		// CONTROL Of the FRIDGE
		// T > Tmax and notOn
		if (this.fridge.getCurrentTemperature() > (this.fridge
				.getTemperatureSP() + this.fridge.getThermoBandDT())
				&& !this.fridge.isCurrentOn()) {
			this.fridge.switchOn();
			this.setRestoreToOn(false);
			this.initCounterRestore();
			return true;
		}
		// T < Tmin and On
		if (this.fridge.getCurrentTemperature() < (this.fridge
				.getTemperatureSP() - this.fridge.getThermoBandDT())
				&& this.fridge.isCurrentOn()) {
			this.fridge.switchOff();
			this.setRestoreToOff(false);
			this.initCounterRestore();
			return true;
		}
		return false;
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
	private void updateAggregator() {
		// TODO Auto-generated method stub
		UpdateMessageContent umc = SimulationMessageFactory
				.getSemiRandomUpdateMessage(this.inputQueueName,
						this.fridge.isCurrentOn());

		this.sendMessage(SimulationMessageFactory.getUpdateMessage(
				this.inputQueueName, ADR_EM_Common.AGG_INPUT_QUEUE, umc));
	}

	private boolean registerToAggregator(UpdateMessageContent firstUpdate) {
		// send registration message
		// TODO change
		this.sendMessage(SimulationMessageFactory
				.getRegisterMessage(this.inputQueueName,
						ADR_EM_Common.AGG_INPUT_QUEUE, firstUpdate));
		SimulationMessage reg = this.waitForMessage();
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
					AMQP.BasicProperties properties, byte[] body)
					throws IOException {
				SimulationMessage sm = null;
				try {
					sm = (SimulationMessage) SimulationMessage
							.deserialize(body);
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
				this.lastInstruction = (InstructionsMessageContent) sm
						.getContent();
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
		return counterRestoreMax;
	}

	public void setCounterRestoreMax(int counterRestoreMax) {
		this.counterRestoreMax = counterRestoreMax;
	}

}
