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
    public enum ConsumerState {
	IDLE, AVAILABLE_UNDER, AVAILABLE_OVER, MONITORING_OVER, MONITORING_UNDER, INOPERATIVE_UNDER, INOPERATIVE_OVER, REACTING_UNDER, REACTING_OVER, DEAD_CONTROL_UNDER, DEAD_CONTROL_OVER
    }

    // STATE OF THE CONSUMER
    private ConsumerState myState = ConsumerState.IDLE;

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

    public ConsumerState getMyState() {
	return myState;
    }

    public void setMyStateExcludeDeadControlUnder(ConsumerState myState, double freqReactUnder) {
	if (freqReactUnder < FrequencyReader.NOMINAL_FREQ) {
	    this.myState = myState;
	}
    }

    public void setMyStateExcludeDeadControlOver(ConsumerState myState, double freqReactOver) {
	if (freqReactOver > FrequencyReader.NOMINAL_FREQ) {
	    this.myState = myState;
	}
    }

    public void setMyState(ConsumerState myState) {
	this.myState = myState;
    }

    public boolean isAllocated()
    { 
	//if notallocated
	if (this.myState.compareTo(ConsumerState.IDLE) == 0 || this.myState.compareTo(ConsumerState.AVAILABLE_UNDER) == 0 || this.myState.compareTo(ConsumerState.AVAILABLE_OVER) == 0)
	{
	    return false;
	}
	return true;
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
