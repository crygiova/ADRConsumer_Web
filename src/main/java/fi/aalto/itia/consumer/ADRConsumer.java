package fi.aalto.itia.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.models.FridgeController;
import fi.aalto.itia.models.FridgeModel;

public class ADRConsumer extends SimulationElement {
    /**
	 * 
	 */
    protected static final Logger logger = LoggerFactory.getLogger(ADRConsumer.class);
    private static final long serialVersionUID = 4328592954437775437L;
    // prefix for queue names
    private static final String PREFIX_INPUT_QUEUE = "adrc_";
    //

    private FridgeController fridgeController;
    private final int ID;

    public ADRConsumer(int ID, FridgeModel fridge) {
	super(PREFIX_INPUT_QUEUE + ID);
	this.ID = ID;
	this.setFridgeController(new FridgeController(fridge));
    }

    public int getID() {
	return ID;
    }

    public FridgeController getFridgeController() {
	return fridgeController;
    }

    public void setFridgeController(FridgeController fridgeController) {
	this.fridgeController = fridgeController;
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
