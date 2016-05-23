package fi.aalto.adrem_consumer;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.consumer.ADRConsumer;
import fi.aalto.itia.consumer.FrequencyReader;
import fi.aalto.itia.consumer.StatsAggregator;
import fi.aalto.itia.models.FridgeFactory;
import fi.aalto.itia.models.FridgeManager;
import fi.aalto.itia.util.Utility;

/**
 * Handles requests for the application home page.
 */
@Controller
public class ConsumerController {

	private static final String FILE_NAME_PROPERTIES = "config.properties";
	private static final String NUMBER_OF_CONSUMERS = "N_CONSUMERS";

	private static final Logger logger = LoggerFactory
			.getLogger(ConsumerController.class);

	private static Properties properties;

	private static Boolean simulationStarted = false;
	private static Integer numberOfConsumers = 0;
	/**
	 * ArrayList which will contain all the Simulation elements
	 */
	public static ArrayList<ADRConsumer> simulationElements = new ArrayList<ADRConsumer>();
	/**
	 * ArrayList of Threads Objects for each simulation element
	 */
	public static ArrayList<Thread> threads = new ArrayList<Thread>();

	public static FridgeManager fridgeManager;
	public static Thread tFridgeManager;
	public static StatsAggregator statsAggregated;
	public static Thread tStatsAggregated;
	// TODO WITH THIS WAY you should restart the server to apply changes in the
	// config file! Better way TODO
	static {
		properties = Utility.getProperties(FILE_NAME_PROPERTIES);
		numberOfConsumers = Integer.parseInt(properties
				.getProperty(NUMBER_OF_CONSUMERS));
	}

	/**
	 * Simply selects the home view to render by returning its name.
	 */
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String home(Locale locale, Model model) {

		Date date = new Date();
		DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.LONG,
				DateFormat.LONG, locale);

		String formattedDate = dateFormat.format(date);
		model.addAttribute("simStarted", simulationStarted);

		model.addAttribute("serverTime", formattedDate);

		return "home";
	}

	@RequestMapping(value = "/startCons", method = RequestMethod.GET)
	public String startCons(Locale locale, Model model) {

		if (!simulationStarted) {
			initConsumers();
			startThreads();
			simulationStarted = true;
		}

		return "redirect:";
	}

	@RequestMapping(value = "/simulationStarted", method = RequestMethod.GET)
	public String simulationStartedController(Locale locale, Model model) {
		return simulationStarted.toString();
	}

	@RequestMapping(value = "/stopCons", method = RequestMethod.GET)
	public String stopCons(Locale locale, Model model) {
		if (simulationStarted) {
			// TODO gather data before ending the simulation or before setting
			// the simulation element null
			if (simulationElements != null) {
				endOfSimulation();
				simulationElements = null;
			}
			simulationStarted = false;
		}
		return "redirect:";
	}

	@RequestMapping(value = "/consumers", method = RequestMethod.GET)
	public @ResponseBody String consumers(Locale locale, Model model) {
		String json = new Gson().toJson(simulationElements.get(0).getFridge()
				.getOnOffList());
		return json;
	}

	@RequestMapping(value = "/consumers/{id}", method = RequestMethod.GET)
	public @ResponseBody String consumer(@PathVariable(value = "id") int index) {
		String json = "";
		// onli the elements with the expose annotation are returned. @exposed
		// annotation of gson library
		Gson jsonGen = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
				.create();
		if (simulationElements.size() > index)
			json = jsonGen.toJson(simulationElements.get(index).getFridge());
		return json;
	}

	@RequestMapping(value = "/consumers/number", method = RequestMethod.GET)
	public @ResponseBody String consumer() {
		String jsonString = "";
		Gson jsonGson = new Gson();
		jsonString = jsonGson.toJson(simulationElements.size());
		return jsonString;
	}
	
	@RequestMapping(value = "/aggStats", method = RequestMethod.GET)
	public @ResponseBody String aggStats() {
		String json = "";
		// onli the elements with the expose annotation are returned. @exposed
		// annotation of gson library
		Gson jsonGen = new GsonBuilder().excludeFieldsWithoutExposeAnnotation()
				.create();
		if(statsAggregated!= null)
			json = jsonGen.toJson(statsAggregated);
		return json;
	}

	public static void initConsumers() {
		// generate fridges

		fridgeManager = new FridgeManager(
				FridgeFactory.getNFridges(numberOfConsumers));

		// SPEED up the first hour temperature + control with thresholds
		fridgeManager.speedUp();
		statsAggregated = new StatsAggregator(fridgeManager.getFridges());
		// add Consumers
		simulationElements = new ArrayList<ADRConsumer>();
		// Add as much as clients you want theoretically
		for (int i = 0; i < numberOfConsumers; i++) {
			simulationElements.add(i, new ADRConsumer(i, fridgeManager
					.getFridges().get(i)));
		}
	}

	public static void startThreads() {
		threads.clear();
		for (SimulationElement r : simulationElements) {
			threads.add(new Thread(r));
		}
		for (Thread thread : threads) {
			thread.start();
		}
		// Start updating fridges dynamics
		tFridgeManager = new Thread(fridgeManager);
		tFridgeManager.start();
		tStatsAggregated = new Thread(statsAggregated);
		tStatsAggregated.start();
		// Start reading frequency
		FrequencyReader.startFrequencyReader();
	}

	/**
	 * Procedure which will end the simulation of all the SimulationElements
	 */
	public synchronized static void endOfSimulation() {
		for (SimulationElement simulationElement : simulationElements) {
			simulationElement.setKeepGoing(false);
			simulationElement.closeConnection();
		}
		simulationElements.clear();
		threads.clear();
		
		fridgeManager.setKeepGoing(false);
		tFridgeManager= null;
		statsAggregated.setKeepGoing(false);
		tStatsAggregated = null;
		// STop Frequency Reader
		FrequencyReader.setKeepReadingToFalse();
	}

}
