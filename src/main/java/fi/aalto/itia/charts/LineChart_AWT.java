package fi.aalto.itia.charts;

import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RefineryUtilities;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

public class LineChart_AWT extends ApplicationFrame {

	private DefaultCategoryDataset dataset = new DefaultCategoryDataset();

	public LineChart_AWT(String applicationTitle, String chartTitle) {
		super(applicationTitle);
		JFreeChart lineChart = ChartFactory.createLineChart(chartTitle,
				"Time", "Charge/Discharge", getDataset(),
				PlotOrientation.VERTICAL, true, true, false);

		ChartPanel chartPanel = new ChartPanel(lineChart);
		chartPanel.setPreferredSize(new java.awt.Dimension(560, 367));
		setContentPane(chartPanel);
	}

	public DefaultCategoryDataset getDataset() {
		return dataset;
	}

	public <T> void addValueToDataSet(double value, Integer rowKey, Comparable<T> columnKey) {
		dataset.addValue(value, rowKey, columnKey);
	}
}
