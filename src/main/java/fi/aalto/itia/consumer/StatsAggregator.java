package fi.aalto.itia.consumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import org.omg.CORBA.FREE_MEM;

import com.google.gson.annotations.Expose;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import fi.aalto.itia.adr_em_common.ADR_EM_Common;
import fi.aalto.itia.adr_em_common.AgingADRConsumer;
import fi.aalto.itia.adr_em_common.SimulationElement;
import fi.aalto.itia.adr_em_common.SimulationMessage;
import fi.aalto.itia.adr_em_common.SimulationMessageFactory;
import fi.aalto.itia.adr_em_common.StatsToAggUpdateContent;

public class StatsAggregator extends SimulationElement {

    /**
	 * */

    private static final long serialVersionUID = 3694387787071215724L;
    private static final int FREQ_UPDATES = 3;// in sec
    private static final int AGING_UPDATES = 20;// Updates are AGING_UPDATED *
						// FREQ_UPDATES sec
    // 20 = 1 every min since FREQ_UPDATES
    private static final int FREQ_UPDATES_NOMINAL_CONS_TO_AGG = 1;
    private static int counter = 0;

    @Expose
    private ArrayList<AgingADRConsumer> consumersAging;
    @Expose
    private ArrayList<Double> aggregatedConsumption;

    @Expose
    // takes into account only which is at any given moment the ADR of the
    // consumers (due to the problem of the constant nominal consumption)
    private ArrayList<Double> aggregatedADR;
    @Expose
    // desired ADR as defined in the paper
    private ArrayList<Double> desiredADR;
    private double desiredADRValue = 0;

    @Expose
    private ArrayList<Double> frequency;
    @Expose
    private ArrayList<Double> filteredFrequency;
    private ArrayList<ADRConsumer> consumers;
    private Double currentAggregatedConsumption;
    private Double currentADR;

    @Expose
    // Nominal base consumption
    private ArrayList<Double> baseLevelAgg;
    private Double currentBaseLevelAgg = 0d;

    private boolean keepGoing = true;
    // stats start time
    private long startTime;
    @Expose
    private long currentTime;

    private boolean aggAllocationUpdate = false;
    @Expose
    // Used for taking register of updates of the aggregator to the consumers
    private ArrayList<Integer> updateAllocation;

    // Consumers states
    @Expose
    private ArrayList<Integer> idleState;
    @Expose
    private ArrayList<Integer> monitoringOverState;
    @Expose
    private ArrayList<Integer> monitoringUnderState;
    @Expose
    private ArrayList<Integer> inoperativeUnderState;
    @Expose
    private ArrayList<Integer> inoperativeOverState;
    @Expose
    private ArrayList<Integer> reactingUnderState;
    @Expose
    private ArrayList<Integer> reactingOverState;
    @Expose
    private ArrayList<Integer> deadUnderState;
    @Expose
    private ArrayList<Integer> deadOverState;

    private int counterUpdateAging = 0;

    /**
     * @param simulationElements
     */
    public StatsAggregator(ArrayList<ADRConsumer> simulationElements) {
	super(ADR_EM_Common.STATS_NAME_QUEUE);
	this.consumers = simulationElements;
	frequency = new ArrayList<Double>();
	filteredFrequency = new ArrayList<Double>();
	aggregatedConsumption = new ArrayList<Double>();
	consumersAging = new ArrayList<AgingADRConsumer>();
	updateAllocation = new ArrayList<Integer>();
	aggregatedADR = new ArrayList<Double>();
	baseLevelAgg = new ArrayList<Double>();
	desiredADR = new ArrayList<Double>();
	// XXX Consumers STates
	idleState = new ArrayList<Integer>();
	monitoringOverState = new ArrayList<Integer>();
	monitoringUnderState = new ArrayList<Integer>();
	inoperativeOverState = new ArrayList<Integer>();
	inoperativeUnderState = new ArrayList<Integer>();
	reactingOverState = new ArrayList<Integer>();
	reactingUnderState = new ArrayList<Integer>();
	deadOverState = new ArrayList<Integer>();
	deadUnderState = new ArrayList<Integer>();

	// init aging for all consumers
	for (ADRConsumer cons : consumers) {
	    if (cons instanceof AggregatorADRConsumer) {
		consumersAging.add(((AggregatorADRConsumer) cons).getAging());
	    }
	}
	startConsumingMq();
    }

    @Override
    public void run() {
	// init start time
	startTime = System.currentTimeMillis();
	while (keepGoing) {
	    try {
		Thread.sleep(FREQ_UPDATES * ADR_EM_Common.ONE_SECOND);
	    } catch (InterruptedException e) {
		e.printStackTrace();
	    }

	    // if the aggregator has sent an update to the consumers
	    if (aggAllocationUpdate == true) {
		aggAllocationUpdate = false;
		updateAllocation.add(aggregatedConsumption.size());
	    }

	    // aggregated consumption
	    if (counterUpdateAging++ > AGING_UPDATES) {
		Collections.sort(this.consumersAging, AgingADRConsumer.DescSortByTotal);
	    }

	    currentADR = 0d;
	    currentAggregatedConsumption = 0d;
	    int idle = 0, reactUnder = 0, reactOver = 0, monitorUnder = 0, monitorOver = 0;
	    int inopUnder = 0, inopOver = 0, deadOver = 0, deadUnder = 0;

	    for (ADRConsumer cons : consumers) {
		currentAggregatedConsumption += cons.getFridgeController().getFridge()
			.getCurrentElectricPower();
		// he is consuming for ADR
		// TODO this is to see which is the real ADR ina given moment
		if (cons.getFridgeController().isRestoreToOff()) {
		    currentADR += cons.getFridgeController().getFridge().getPtcl();
		} else if (cons.getFridgeController().isRestoreToOn()) {// he is
									// switched
									// off
									// forADR
		    currentADR -= cons.getFridgeController().getFridge().getPtcl();
		}
		// TODO count the states
		switch (cons.getMyState()) {
		case IDLE:
		    idle++;
		    break;
		case REACTING_UNDER:
		    reactUnder++;
		    break;
		case REACTING_OVER:
		    reactOver++;
		    break;
		case MONITORING_UNDER:
		    monitorUnder++;
		    break;
		case MONITORING_OVER:
		    monitorOver++;
		    break;
		case INOPERATIVE_UNDER:
		    inopUnder++;
		    break;
		case INOPERATIVE_OVER:
		    inopOver++;
		    break;
		case DEAD_CONTROL_OVER:
		    deadOver++;
		    break;
		case DEAD_CONTROL_UNDER:
		    deadUnder++;
		    break;
		default:
		    break;
		}
	    }
	    // real consumption of the system
	    aggregatedConsumption.add(currentAggregatedConsumption);
	    // quantity of ADR relative to zero
	    aggregatedADR.add(currentADR);
	    // first loop
	    double currentBase = 0d;
	    if (currentBaseLevelAgg == 0d) {
		currentBase = currentAggregatedConsumption;
	    } else {
		currentBase = currentBaseLevelAgg;
	    }
	    baseLevelAgg.add(currentBase);
	    desiredADRValue = FrequencyReader.calculateDesiredADR(currentBase);
	    desiredADR.add(desiredADRValue);
	    // update current time simulation
	    currentTime = System.currentTimeMillis() - startTime;

	    frequency.add(FrequencyReader.getCurrentFreqValue());
	    filteredFrequency.add(FrequencyReader.getFilteredFrequency());
	    // send update to the aggregator
	    if (++counter > FREQ_UPDATES_NOMINAL_CONS_TO_AGG) {
		counter = 0;
		this.sendMessage(SimulationMessageFactory.getStatsToAggUpdateMessage(
			inputQueueName, ADR_EM_Common.AGG_INPUT_QUEUE, new StatsToAggUpdateContent(
				currentAggregatedConsumption - currentADR)));
	    }
	    // States of the consumers
	    idleState.add(idle);
	    monitoringOverState.add(monitorOver);
	    monitoringUnderState.add(monitorUnder);
	    inoperativeOverState.add(inopOver);
	    inoperativeUnderState.add(inopUnder);
	    reactingOverState.add(reactOver);
	    reactingUnderState.add(reactUnder);
	    deadOverState.add(deadOver);
	    deadUnderState.add(deadUnder);
	}
    }

    // Used to get a message from the Aggregator when it sends new updates to
    // the consumers
    public void startConsumingMq() {
	Consumer consumer = new DefaultConsumer(dRChannel) {
	    @Override
	    public void handleDelivery(String consumerTag, Envelope envelope,
		    AMQP.BasicProperties properties, byte[] body) throws IOException {
		SimulationMessage sm = null;
		try {
		    sm = (SimulationMessage) SimulationMessage.deserialize(body);
		} catch (ClassNotFoundException e) {
		    e.printStackTrace();
		}
		if (sm != null) {

		    if (sm.getHeader().compareTo(ADR_EM_Common.STATS_TO_AGG_HEADER) == 0) {
			// if the response from the Agg to the STATS_TO AGG
			// HEADER with the current baase nominal
			currentBaseLevelAgg = ((StatsToAggUpdateContent) sm.getContent())
				.getCurrentNominalAggregatedConsumption();
		    } else {
			aggAllocationUpdate = true;
		    }
		}
	    }
	};
	try {
	    dRChannel.basicConsume(inputQueueName, true, consumer);
	} catch (IOException e) {
	    e.printStackTrace();
	}
    }

    public ArrayList<Double> getAggregatedConsumption() {
	return aggregatedConsumption;
    }

    public ArrayList<Double> getFrequency() {
	return frequency;
    }

    public void setKeepGoing(boolean b) {
	keepGoing = b;
    }

    @Override
    public void scheduleTasks() {
	// Auto-generated method stub

    }

    @Override
    public void executeTasks() {
	// Auto-generated method stub

    }

    @Override
    public void elaborateIncomingMessages() {
	// Auto-generated method stub

    }

}
