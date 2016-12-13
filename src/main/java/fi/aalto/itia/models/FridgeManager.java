package fi.aalto.itia.models;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FridgeManager implements Runnable {

    @SuppressWarnings("unused")
    private static final Logger logger = LoggerFactory.getLogger(FridgeManager.class);
    private static final int INIT_TIME = 24 * 3600;//24 * 3600;// one hour of speed up
    private static final int UPDATE_TIME = 900;
    // during speed up keeps the lists updated or not
    private static final boolean KEEP_INIT_LISTS_UPDATED = false;
    private static final boolean KEEP_SIMULATION_LISTS_UPDATED = false;

    ArrayList<FridgeModel> fridges = new ArrayList<FridgeModel>();
    private boolean keepGoing = true;

    // TODO FIND A WAY TO SPEED UP THE INIT PROCESS OF THE FRIDGES for the first
    // hour maybe!

    // Number of fridges
    // TODO implement singleton
    /**
     * @param numberOfFridges
     * @param speedUp
     *            asks to initiate with
     */
    public FridgeManager(ArrayList<FridgeModel> fridges) {
	this.fridges = fridges;
	// if true
    }

    public FridgeManager() {
    }

    // If called speeds up the dynamics of the fridges for INIT_TIME
    public void speedUp() {
	for (int i = 0; i < INIT_TIME; i++) {
	    for (FridgeModel fridgeModel : fridges) {
		if (i == 0) {
		    fridgeModel.setUpdateLists(KEEP_INIT_LISTS_UPDATED);
		}
		fridgeModel.updateTemperature();
		this.controlFridgeWithThresholds(fridgeModel);
		if (i == INIT_TIME - 1) {
		    fridgeModel.setUpdateLists(KEEP_SIMULATION_LISTS_UPDATED);
		}
	    }
	}
    }

    @Override
    public void run() {
	while (keepGoing) {
	    try {
		Thread.sleep(UPDATE_TIME);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }
	    for (FridgeModel fridgeModel : fridges) {
		// TODO Only temperature Update -> No control
		fridgeModel.updateTemperature();
		// TODO also limits temp control automatic done by the fridges
		// theirselves
		// this.controlFridgeWithThresholds(fridgeModel);
	    }
	}
    }

    // Basic control + freq control
    private boolean controlFridgeWithThresholds(FridgeModel fridgeModel) {
	// CONTROL Of the FRIDGE
	// T > Tmax and notOn
	if (fridgeModel.getCurrentTemperature() > (fridgeModel.getTemperatureSP() + fridgeModel
		.getThermoBandDT()) && !fridgeModel.isCurrentOn()) {
	    fridgeModel.switchOn();
	    return true;
	}
	// T < Tmin and On
	if (fridgeModel.getCurrentTemperature() < (fridgeModel.getTemperatureSP() - fridgeModel
		.getThermoBandDT()) && fridgeModel.isCurrentOn()) {
	    fridgeModel.switchOff();
	    return true;
	}
	return false;
    }

    public void addFridge(FridgeModel fm) {
	fridges.add(fm);
    }

    public ArrayList<FridgeModel> getFridges() {
	return fridges;
    }

    public boolean isKeepGoing() {
	return keepGoing;
    }

    public void setKeepGoing(boolean keepGoing) {
	this.keepGoing = keepGoing;
    }

}
