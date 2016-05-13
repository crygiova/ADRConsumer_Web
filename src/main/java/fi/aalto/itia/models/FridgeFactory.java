package fi.aalto.itia.models;

import java.util.ArrayList;
import java.util.Random;

/** Factory class to generate fridges */
public class FridgeFactory {

	private static final double CONST_321 = 3.21d;

	private static double coeffOfPerf, ptcl, mcMin, mcMax, thermalConductanceA,
			tau, temperatureSP, thermoBandDT, tAmb, t0, q0;

	public static FridgeModel getFridge() {
		Random r = new Random();

		coeffOfPerf = 3d;
		ptcl = 70d;
		mcMin = 7.9d;
		mcMax = 32;
		thermalConductanceA = CONST_321;
		tau = CONST_321 / (mcMax * 3600)
				+ (CONST_321 / (mcMin * 3600) - 3.21 / (mcMax * 3600))
				* r.nextDouble();
		temperatureSP = 4.5 + (5.5 - 4.5) * r.nextDouble();
		thermoBandDT = 1 + (2 - 1) * r.nextDouble();
		tAmb = 17 + (23 - 17) * r.nextDouble();
		t0 = (temperatureSP - thermoBandDT) / 2d + thermoBandDT
				* r.nextDouble();
		q0 = ptcl * Math.round(0.65 * r.nextDouble());
		return new FridgeModel(coeffOfPerf, ptcl, mcMin, mcMax,
				thermalConductanceA, tau, temperatureSP, thermoBandDT, tAmb,
				t0, q0);
	}

	public static ArrayList<FridgeModel> getNFridges(int size) {
		ArrayList<FridgeModel> fridges = new ArrayList<FridgeModel>();
		for (int i = 0; i < size; i++) {
			fridges.add(getFridge());
		}
		return fridges;
	}

}
