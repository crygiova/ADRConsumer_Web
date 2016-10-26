package fi.aalto.itia.consumer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

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
    private ArrayList<Double> frequency;
    private ArrayList<ADRConsumer> consumers;
    private Double currentAggregatedConsumption;
    private Double currentADR;

    @Expose
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

    private int counterUpdateAging = 0;

    /**
     * @param simulationElements
     */
    public StatsAggregator(ArrayList<ADRConsumer> simulationElements) {
	super(ADR_EM_Common.STATS_NAME_QUEUE);
	this.consumers = simulationElements;
	frequency = new ArrayList<Double>();
	aggregatedConsumption = new ArrayList<Double>();
	consumersAging = new ArrayList<AgingADRConsumer>();
	updateAllocation = new ArrayList<Integer>();
	aggregatedADR = new ArrayList<Double>();
	baseLevelAgg = new ArrayList<Double>();
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
	    for (ADRConsumer cons : consumers) {
		currentAggregatedConsumption += cons.getFridge().getCurrentElectricPower();
		// he is consuming for ADR
		// TODO this is to see which is the real ADR ina given moment
		if (cons.isRestoreToOff()) {
		    currentADR += cons.getFridge().getPtcl();
		} else if (cons.isRestoreToOn()) {// he is switched off forADR
		    currentADR -= cons.getFridge().getPtcl();
		}
	    }
	    aggregatedConsumption.add(currentAggregatedConsumption);
	    aggregatedADR.add(currentADR);
	    frequency.add(FrequencyReader.getCurrentFreqValue());
	    // update current time simulation
	    currentTime = System.currentTimeMillis() - startTime;
	    // first loop
	    if (currentBaseLevelAgg == 0d) {
		baseLevelAgg.add(currentAggregatedConsumption - currentADR);
	    } else {
		baseLevelAgg.add(currentBaseLevelAgg);
	    }
	    // send update to the aggregator
	    if (++counter > FREQ_UPDATES_NOMINAL_CONS_TO_AGG) {
		counter = 0;
		this.sendMessage(SimulationMessageFactory.getStatsToAggUpdateMessage(
			inputQueueName, ADR_EM_Common.AGG_INPUT_QUEUE, new StatsToAggUpdateContent(
				currentAggregatedConsumption - currentADR)));
	    }
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
			System.out
				.println("/*-/*-/*-/*/*-/*/*-/*-/*-/*/*-/*-/*/*/*-/*-/*/*-*/*--*/*-*/*-**-*/*-*/*-*/*-*/*-*/-*/-*");
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
