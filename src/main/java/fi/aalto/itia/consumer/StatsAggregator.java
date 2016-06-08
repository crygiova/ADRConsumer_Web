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

public class StatsAggregator extends SimulationElement {

    /**
	 * 
	 */
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3694387787071215724L;
    private static final int FREQ_UPDATES = 3;// in sec
    private static final int AGING_UPDATES = 20;// Updates are AGING_UPDATED *
						// FREQ_UPDATES sec
    
    @Expose
    private ArrayList<AgingADRConsumer> consumersAging;
    @Expose
    private ArrayList<Double> aggregatedConsumption;

    @Expose
    private ArrayList<Double> frequency;
    private ArrayList<ADRConsumer> consumers;
    private Double currentAggregatedConsumption;
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
	// init aging for all consumers
	for (ADRConsumer cons : consumers) {
	    consumersAging.add(cons.getAging());
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

	    currentAggregatedConsumption = 0d;
	    for (ADRConsumer cons : consumers) {
		currentAggregatedConsumption += cons.getFridge().getCurrentElectricPower();
	    }

	    aggregatedConsumption.add(currentAggregatedConsumption);
	    frequency.add(FrequencyReader.getCurrentFreqValue());
	    // update current time simulation
	    currentTime = System.currentTimeMillis() - startTime;
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
		    System.out
			    .println("/*-/*-/*-/*/*-/*/*-/*-/*-/*/*-/*-/*/*/*-/*-/*/*-*/*--*/*-*/*-**-*/*-*/*-*/*-*/*-*/-*/-*");
		    aggAllocationUpdate = true;
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
