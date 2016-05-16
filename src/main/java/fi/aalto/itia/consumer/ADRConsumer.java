package fi.aalto.itia.consumer;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.adrem_consumer.ADRConsumerController;
import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.adr_em_common.SimulationMessage;
import fi.aalto.itia.adr_em_common.SimulationMessageFactory;
import fi.aalto.itia.adr_em_common.UpdateMessageContent;
import fi.aalto.itia.models.FridgeModel;

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

	public ADRConsumer(int ID, FridgeModel fridge) {
		super(PREFIX_INPUT_QUEUE + ID);
		this.ID = ID;
		this.fridge = fridge;
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

	@Override
	public void run() {
		this.startConsumingMq();
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
		// TODO get first UpdateMessageContent, register with random update message
		this.registerToAggregator(SimulationMessageFactory
				.generateRandomUpdateMessage(this.inputQueueName));
		Random rand = new Random();
		while (this.keepGoing) {
			int msecUpdate = 5 * 60000;

			try {
				Thread.sleep(1 + msecUpdate
						- (Math.round(msecUpdate * rand.nextFloat())));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			// Random Control
			this.fridge.switchOnOff(rand.nextBoolean());
		}
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
}
