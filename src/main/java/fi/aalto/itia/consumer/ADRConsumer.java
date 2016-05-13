package fi.aalto.itia.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.adrem_consumer.ADRConsumerController;
import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.adr_em_common.SimulationMessageFactory;
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
		// TODO init procedure ofthe consumer
		int i = 0;
		// TODO Registration message (it could be that in the content of the
		// registration message there is already the first update
		// TODO think how to monitor the frequency (it could be done centrally
		// by he main consumer every second and there could be a method to call)

		// TODO decide also how often the updates are sent, and with which
		// policy (frequency & every status change?)
		while (i++ < 1) {
			try {
				this.sendMessage(SimulationMessageFactory.getRegisterMessage(
						this.inputQueueName, ADR_EM_Common.AGG_INPUT_QUEUE));
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
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

}
