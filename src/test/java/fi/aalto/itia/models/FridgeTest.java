package fi.aalto.itia.models;

import org.junit.Test;

public class FridgeTest {

    @Test
    public void testSecondsToTemp() {

	FridgeModel fridgeModel = FridgeFactory.getFridge();
	for (int i = 0; i < 2600; i++) {
	    fridgeModel.updateTemperature();
	}
	System.out.println(fridgeModel.toString());
	double maxT = fridgeModel.getTemperatureSP() + fridgeModel.getThermoBandDT();
	double minT = fridgeModel.getTemperatureSP() - fridgeModel.getThermoBandDT();

	System.out.println("Max : " + maxT + " Min:" + minT);
	fridgeModel.DELAY_BEFORE_CHANGING_STATUS = -1;
	// fridge ON
	fridgeModel.switchOn();
	System.out.println("TURN ON\n\n");
	System.out.println("Current Temp: " + fridgeModel.getCurrentTemperature());
	System.out.println("To MAX Time: " + fridgeModel.getSecondsToTempMaxLimit());
	System.out.println("To MIN Time: " + fridgeModel.getSecondsToTempMinLimit());
	if (!fridgeModel.isPossibleToSwitchOff()) {

	    fridgeModel.setPossibleToSwitchOff(true);
	}

	fridgeModel.switchOff();
	System.out.println("TURN OFF\n\n");
	System.out.println("Current Temp: " + fridgeModel.getCurrentTemperature());
	System.out.println("To MAX Time: " + fridgeModel.getSecondsToTempMaxLimit());
	System.out.println("To MIN Time: " + fridgeModel.getSecondsToTempMinLimit());

    }

}
