package traminer.spark.trajectory.clost;

import java.io.Serializable;

/**
 * Parameters for CloST partitioning.
 * 
 * @see SparkCloSTPartitioner
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class CloSTParameters implements Serializable {
	/** Spatial model boundaries */
	private final double minX, maxX; 
	private final double minY, maxY;
	/** Time window size - Level 3 */
	private final long timeWindow;
	
	/** QuadTree spatial parameters */
	private final int sampleSize;
	private final int nodesCapacity;
	
	/**
	 * Set the CloST partitioning parameters.
	 * 
	 * @param minX Spatial area coverage, minX.
	 * @param maxX Spatial area coverage, maxX.
	 * @param minY Spatial area coverage, minY.
	 * @param maxY Spatial area coverage, maxY.
	 * @param timeWindow Time window size.
	 * @param nodesCapacity For quadtree partitioning
	 * @param sampleSize Number of trajectories to sample
	 */
	public CloSTParameters(double minX, double maxX, double minY, double maxY, long timeWindow, int nodesCapacity, int sampleSize) {
		this.minX = minX;
		this.maxX = maxX;
		this.minY = minY;
		this.maxY = maxY;
		this.timeWindow = timeWindow;
		this.sampleSize = sampleSize;
		this.nodesCapacity = nodesCapacity;
	}

	/**
	 * @return The number of trajectories to sample for Spark Quadtree construction.
	 */
	public int getSampleSize() {
		return sampleSize;
	}

	/**
	 * @return Nodes capacity for Quadtree dynamic partitioning.
	 */
	public int getNodesCapacity() {
		return nodesCapacity;
	}

	public double minX() {
		return minX;
	}

	public double maxX() {
		return maxX;
	}

	public double minY() {
		return minY;
	}

	public double maxY() {
		return maxY;
	}

	/**
	 * @return The time window used in the temporal partitioning (level 3)
	 */
	public long getTimeWindow() {
		return this.timeWindow;
	}
}
