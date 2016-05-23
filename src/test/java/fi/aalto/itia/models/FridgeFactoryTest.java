package fi.aalto.itia.models;

import java.io.IOException;
import java.util.ArrayList;

import org.jfree.ui.RefineryUtilities;
import org.junit.Test;

import fi.aalto.itia.charts.LineChart_AWT;

public class FridgeFactoryTest {

	@Test
	public void testGetFridge() {
		FridgeModel fridgeModel = FridgeFactory.getFridge();
		System.out.println(fridgeModel.toString());
	}

	@Test
	public void testGetSingleFridge() {
		ArrayList<FridgeModel> a = FridgeFactory.getNFridges(1);
		for (FridgeModel fridgeModel : a) {
			System.out.println(fridgeModel.toString());
		}
		Integer i = 0;
		int prevI = 0;
		boolean prev = false;
		LineChart_AWT chartTemperature = new LineChart_AWT("Charging",
				"Charging Time");
		LineChart_AWT chartConsumption = new LineChart_AWT("Charging",
				"Charging Time");
		while (i++ < (4 * 3600)) {
			for (FridgeModel fridgeModel : a) {
				fridgeModel.updateTemperature();
				prev = fridgeModel.isCurrentOn();
				// CONTROL Of the FRIDGE
				// T > Tmax and notOn
				if (fridgeModel.getCurrentTemperature() > (fridgeModel
						.getTemperatureSP() + fridgeModel.getThermoBandDT())
						&& !fridgeModel.isCurrentOn())
					fridgeModel.switchOn();
				// T < Tmin and On
				if (fridgeModel.getCurrentTemperature() < (fridgeModel
						.getTemperatureSP() - fridgeModel.getThermoBandDT())
						&& fridgeModel.isCurrentOn())
					fridgeModel.switchOff();

				if (prev != fridgeModel.isCurrentOn()) {
					System.out.println(i - prevI);
					prevI = i;
					System.out.println(fridgeModel.getCurrentTemperature()
							+ " O:" + fridgeModel.isCurrentOn());
				}
				// TODO ADDING TO THE GRAPH
				if (i % 50 == 0) {
					chartTemperature.addValueToDataSet(
							fridgeModel.getCurrentTemperature(),
							fridgeModel.getID(), i);
					chartConsumption.addValueToDataSet(
							fridgeModel.getCurrentElectricPower(),
							fridgeModel.getID(), i);
				}
			}
		}
		chartTemperature.pack();
		RefineryUtilities.centerFrameOnScreen(chartTemperature);
		chartTemperature.setVisible(true);
		chartConsumption.pack();
		RefineryUtilities.centerFrameOnScreen(chartConsumption);
		chartConsumption.setVisible(true);
		
		System.out.println("EXIT CONSOLE");
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("ENDENDENDENDENDEND");
	}

	@Test
	public void testGetArrayFridge() {
		ArrayList<FridgeModel> a = FridgeFactory.getNFridges(10);
		for (FridgeModel fridgeModel : a) {
			System.out.println(fridgeModel.toString());
		}
		Integer i = 0;
		int prevI = 0;
		boolean prev = false;
		LineChart_AWT chartTemperature = new LineChart_AWT("Temperature",
				"Temperature Time");
		LineChart_AWT chartConsumption = new LineChart_AWT("Charging",
				"Charging Time");
		while (i++ < 20600) {
			for (FridgeModel fridgeModel : a) {
				fridgeModel.updateTemperature();
				prev = fridgeModel.isCurrentOn();
				// CONTROL Of the FRIDGE
				// T > Tmax and notOn
				if (fridgeModel.getCurrentTemperature() > (fridgeModel
						.getTemperatureSP() + fridgeModel.getThermoBandDT())
						&& !fridgeModel.isCurrentOn())
					fridgeModel.switchOn();
				// T < Tmin and On
				if (fridgeModel.getCurrentTemperature() < (fridgeModel
						.getTemperatureSP() - fridgeModel.getThermoBandDT())
						&& fridgeModel.isCurrentOn())
					fridgeModel.switchOff();

				if (prev != fridgeModel.isCurrentOn()) {
					System.out.println(i - prevI);
					prevI = i;
					System.out.println(fridgeModel.getCurrentTemperature()
							+ " O:" + fridgeModel.isCurrentOn());
				}
				// TODO ADDING TO THE GRAPH
				// chartTemperature.addValueToDataSet(
				// fridgeModel.getCurrentTemperature(),
				// fridgeModel.getID(), i);
				// chartConsumption.addValueToDataSet(
				// fridgeModel.getCurrentElectricPower(),
				// fridgeModel.getID(), i);
			}
		}
		chartTemperature.pack();
		RefineryUtilities.centerFrameOnScreen(chartTemperature);
		chartTemperature.setVisible(true);
		chartConsumption.pack();
		RefineryUtilities.centerFrameOnScreen(chartConsumption);
		chartConsumption.setVisible(true);
		System.out.println("EXIT CONSOLE");
		try {
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("ENDENDENDENDENDEND");
	}

}
