/*
 * Tan, Haoyu, Wuman Luo, and Lionel M. Ni. 
 * "Clost: a hadoop-based storage system for big spatio-temporal data analytics."
 *  In ACM CIKM, 2012.
 */
package traminer.spark.trajectory.clost;

import java.util.ArrayList;
import java.util.List;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;

import scala.Tuple2;
import traminer.util.spatial.SpatialInterface;
import traminer.util.spatial.objects.st.STPoint;
import traminer.util.spatial.structures.quadtree.QuadTreeModel;
import traminer.util.trajectory.Trajectory;

/**
 * Spark-based implementation of CloST, Hierarchical partitioning 
 * for trajectory data. Build the CloST partitions using Spark's
 * RDD. In this implementation we ignore the Level 1 of CloST,
 * since we consider every trajectory as from a unique source object. 
 * </br>
 * </br> Level 1 - Bucket - By OID 
 * </br> Level 2 - Region - By spatial region - Quadtree
 * </br> Level 3 - Block  - By time interval
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class SparkCloSTPartitioner implements SpatialInterface {
	/** CloST trajectory partitioning parameters */
	private final CloSTParameters params;
	/** The quadtree model for space partitioning */
	private QuadTreeModel quadTreeModel;
	
	/**
	 * Setup CloST partitioning parameters.
	 * 
	 * @param params CloST parameters.
	 */
	public SparkCloSTPartitioner(CloSTParameters params){
		this.params = params;
	}
	
	/**
	 * Perform a CloST hierarchical partitioning on the given RDD of trajectories.
	 * 
	 * @param trajectoryRDD The distributed set of trajectories to partition.
	 * @return A key-value pair containing the CloST index as key, 
	 * and the trajectory points in the partitions as value.
	 */
	public JavaPairRDD<CloSTIndex, STPoint> doCloSTPartitioning(JavaRDD<Trajectory> trajectoryRDD) {
		println("[CLOST] Trajectories partitioning starts at: " + System.currentTimeMillis());

		// we do not consider the Level 1 (Bucket)
		final String oid = "1";
 
		// build quad-tree index - Level 2 (Region)
		SparkSpatialModelBuilder.init(params.minX(), params.minY(), params.maxX(), params.maxY());
		quadTreeModel = SparkSpatialModelBuilder.buildQuadTreeModel(
				trajectoryRDD, params.getSampleSize(), params.getNodesCapacity());
		println("[CLOST] Number of Quad-Nodes: " + quadTreeModel.size());

		// the initial active time of all partitions
		final long initialActiveTime = System.currentTimeMillis();
		
		// do the partitioning, using the spatial model and time window size
		JavaPairRDD<CloSTIndex, STPoint> pointToPartitionRDD = 
			trajectoryRDD.flatMapToPair(trajectory -> {
				// map each trajectory point to its partition (key, value)
				List<Tuple2<CloSTIndex, STPoint>> resultMap = new ArrayList<>();
				for (STPoint point : trajectory) {				
					// find the spatial partition intersecting this point
					// Level 2 index - Region
					final String loc = quadTreeModel.search(point.x(), point.y());

					// find the time block containing this point
					// Level 3 index - Block
					final long time = point.time() / params.getTimeWindow();
					
					// create the CloST index
					CloSTIndex index = new CloSTIndex(oid, loc, ""+time);
					index.setActiveTime(initialActiveTime);
					
					// do the map
					resultMap.add(new Tuple2<CloSTIndex, STPoint>(index, point));
				}
				return resultMap.iterator();
			});
		
		// force partitioning and get information
		long numPoints = pointToPartitionRDD.count();
		long numPartitions = pointToPartitionRDD.keys().distinct().count();
		println("[CLOST] Number of points mapped: " + numPoints);
		println("[CLOST] Number of CloST partitions: " + numPartitions);
		println("[CLOST] Trajectories partitioning finished at: " + System.currentTimeMillis());
		
		return pointToPartitionRDD;
	}
	
	/**
	 * @return The Quadtree model built after partitioning
	 */
	public QuadTreeModel getSpatialModel() {
		return quadTreeModel;
	}
	
}
