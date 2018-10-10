package traminer.spark.trajectory.datamanager;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Paths;
import java.util.List;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.storage.StorageLevel;

import scala.Tuple2;

import traminer.io.IOService;
import traminer.io.log.ObservableLog;
import traminer.io.params.SparkParameters;
import traminer.io.reader.TrajectoryReader;
import traminer.io.spark.SparkContextClient;
import traminer.spark.trajectory.clost.CloSTIndex;
import traminer.spark.trajectory.clost.CloSTParameters;
import traminer.spark.trajectory.clost.SparkCloSTPartitioner;
import traminer.util.Printable;
import traminer.util.spatial.objects.st.STPoint;
import traminer.util.spatial.structures.quadtree.QuadTreeModel;
import traminer.util.trajectory.Trajectory;

/**
 * The Physical Planner is responsible for raw trajectory
 * data loading, partitioning, index construction, and physical 
 * planning in the distributed file system.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class PhysicalPlanner implements Serializable, Printable {
	private final SparkParameters sparkParams;
	private final CloSTParameters clostParams;
	
	/** Whether the physical planning is completed. */
	private boolean physicalPlanningCompleted = true;
	
	/** Whether the physical planner started in hot mode. */
	private boolean hotStart = true;
	
	/** The Quadtree model built after planning - Index*/
	private QuadTreeModel spatialModel;
	
	/** Path to cold partitions on disk - HDFS */
	private final String coldPartitionsLocation;

	/** Number of replications of the input dataset (for experiments)*/
	private static final int REPLICATION = 1;	
	
	/** App log */
	private ObservableLog log = ObservableLog.instance();
	
	/**
	 * Creates a new PhysicalPlanner with the given parameters.
	 * 
	 * @param sparkParams Spark cluster access parameters.
	 * @param coldDataLocation Path to cold data locally.
	 * @param clostParams CloST partitioning configurations.
	 * 
	 * @see SparkCloSTPartitioner
	 */
	public PhysicalPlanner(SparkParameters sparkParams, CloSTParameters clostParams, String coldDataLocation) {
		this.sparkParams = sparkParams;
		this.clostParams = clostParams;
		this.coldPartitionsLocation = coldDataLocation + "\\";
	}
	
	/**
	 * Starts the physical planner with all partitions on-disk (HDFS) after the partitioning.
	 * This option should be used when the available memory for storing the data partitions
	 * is a constraint.
	 * 
	 * @param dataPath Path to trajectory data.
	 * @param numPartitions Number of RDD partitions.
	 * 
	 * @return An empty key/value pair RDD - Since there is no hot partitions in this mode,
	 * the returned RDD is empty.
	 */
	public JavaPairRDD<CloSTIndex, Iterable<STPoint>> startCold(
			final String dataPath, final int numPartitions) {
		log.info("[PHYSICAL PLANNER] Starting Physical Planner in COLD mode.");
		hotStart = false;
		
		// read trajectories and do partitioning
		JavaRDD<Trajectory> trajectoryRDD = readTrajectoryData(
				dataPath, numPartitions, StorageLevel.DISK_ONLY());
		JavaPairRDD<CloSTIndex, Iterable<STPoint>> partitionsRDD = 
				doPartitioning(trajectoryRDD);
		
		// write partitions to disk
		//log.info("[PHYSICAL-PLANNER] Writing partitions to disk.");
		//writePartitions(partitionsRDD);

		// Unpersist the partitions from the RDD. 
		// Set partition as null, and make them cold.
		partitionsRDD = partitionsRDD.mapToPair(partition -> {
			CloSTIndex index = partition._1; 
			index.setHot(false);
			return new Tuple2<CloSTIndex, Iterable<STPoint>>(index, null); 
		});

  	    long partitionsCount = partitionsRDD.keys().distinct().count();
  	    log.finish("[PHYSICAL-PLANNER] Physical Planning completed with (" + partitionsCount + ") partitions.");
  	    physicalPlanningCompleted = true;
  	     
		return partitionsRDD;
	}

	/**
	 * Starts the physical planner with all partitions in-memory after the partitioning.
	 * This option should be used only when there is enough memory available in the
	 * cluster to store the data partitions.
	 * 
	 * @param dataPath Path to trajectory data.
	 * @param numPartitions Number of RDD partitions.
	 * 
	 * @return A cached RDD of partitions. A key-value pair RDD containing the CloST index 
	 * as key, and the list of trajectory points in the partitions as value.
	 */
	public JavaPairRDD<CloSTIndex, Iterable<STPoint>> startHot(
			final String dataPath, final int numPartitions) {
		log.info("[PHYSICAL-PLANNER] Starting Physical Planner in HOT mode.");
		hotStart = true;
		
		// read trajectories and do partitioning
		JavaRDD<Trajectory> trajectoryRDD = readTrajectoryData(
				dataPath, numPartitions, StorageLevel.MEMORY_ONLY());
		JavaPairRDD<CloSTIndex, Iterable<STPoint>> partitionsRDD = 
				doPartitioning(trajectoryRDD);

		// write partitions to disk regardless.
		log.info("[PHYSICAL-PLANNER] Writing partitions to disk.");
		writePartitions(partitionsRDD);

  	    long hotPartitionsCount = partitionsRDD.keys().distinct().count();
  	    log.info("[PHYSICAL-PLANNER] Physical Planning completed with (" + hotPartitionsCount + ") partitions.");
  	    physicalPlanningCompleted = true;
  	  
		return partitionsRDD.cache();
	}
	
	/**
	 * Starts the physical planner with all partitions in-memory. Read the partitions from disk,
	 * this method assumes that the partitioning has already been done, and the partitions are
	 * saved to disk in the 'coldPartitionsLocatio'. This option should be used only when there is 
	 * enough memory available in the cluster to store the data partitions.
	 * 
	 * @param numPartitions Number of RDD partitions.
	 * 
	 * @return A cached RDD of partitions. A key-value pair RDD containing the CloST index 
	 * as key, and the list of trajectory points in the partitions as value.
	 */
	public JavaPairRDD<CloSTIndex, Iterable<STPoint>> startHotFromDisk(final int numPartitions) {
		log.info("[PHYSICAL-PLANNER] Starting Physical Planner in HOT mode.");
		hotStart = true;

		// read partitions from disk
		JavaPairRDD<CloSTIndex, Iterable<STPoint>> partitionsRDD = 
				loadPartitionsFromDisk(numPartitions);

  	    long hotPartitionsCount = partitionsRDD.keys().distinct().count();
  	    log.info("[PHYSICAL-PLANNER] Physical Planning completed with (" + hotPartitionsCount + ") partitions.");
  	    physicalPlanningCompleted = true;
  	  
		return partitionsRDD.cache();
	}
	
	/**
	 * Load all the partition from disk into a RDD.
	 * 
	 * @return The RDD with all partitions from disk.
	 */
	@SuppressWarnings("unchecked")
	private JavaPairRDD<CloSTIndex, Iterable<STPoint>> loadPartitionsFromDisk(final int numPartitions) {
		try {
			// get the list of files on disk (cold partitions)
			List<String> filesPathList = IOService.getFilesPathList(Paths.get(coldPartitionsLocation));
			// read and map each file to a CloST partition into the RDD		
			JavaSparkContext sc = SparkContextClient.getJavaContextInstance(sparkParams);
			JavaRDD<String> pathsRDD = sc.parallelize(filesPathList, numPartitions);
			final long activeTime = System.currentTimeMillis();
			// map each index to the partition of the index (loaded from disk)
			JavaPairRDD<CloSTIndex, Iterable<STPoint>> partitionsRDD = 
				pathsRDD.mapToPair(partitionFilePath ->{
					// map each file to a CloST partition
					Tuple2<CloSTIndex, Iterable<STPoint>> partition = (Tuple2<CloSTIndex, Iterable<STPoint>>)
								IOService.readObject(partitionFilePath);
					// mark partition as hot, and set time
					partition._1.setHot(true);
					partition._1.setActiveTime(activeTime);
					
					return partition;
				});
			return partitionsRDD;
		} catch (Exception e) {
			log.info("[STORAGE-CONTROLLER] Unable to load partitions from disk.");
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * @return Whether the physical planning is completed.
	 */
	public boolean isPhysicalPlanningCompleted() {
		return physicalPlanningCompleted;
	}
	
	/**
	 * @return Whether the physical planner started in HOT mode.
	 */
	public boolean isHotStart() {
		return hotStart;
	}

	/**
	 * @return The Quadtree model built after planning. 
	 * Spatial Index.
	 */
	public QuadTreeModel getSpatialModel() {
		return spatialModel;
	}
	
	/**
	 * Read the trajectories in the given path as a Spark RDD.
	 * 
	 * @param dataPath Path to data files.
	 * @param numPartitions Number of RDD partitions.
	 * 
	 * @return RDD of trajectories.
	 */
	private JavaRDD<Trajectory> readTrajectoryData(
			final String dataPath, final int numPartitions, StorageLevel storageLevel) {
		JavaRDD<Trajectory> trajectoryRDD = null;
		try {
			final String dataFormat = IOService.readResourcesFileContent("trajectory-data-format.tddf");
			trajectoryRDD = TrajectoryReader.readAsSparkRDD(sparkParams, dataPath, dataFormat, numPartitions, false, REPLICATION);
			trajectoryRDD.persist(storageLevel);
		} catch (IOException e) {
			log.error("[PHYSICAL PLANNER] Unable to read trajectory data.");
			e.printStackTrace();
		}
		
		return trajectoryRDD;
	}
	
	/**
	 * Partition the given trajectory RDD using CloST partitioning.
	 * 
	 * @param trajectoryRDD The trajectory RDD to partition.
	 * 
	 * @return The CloST partitions.
	 */
	private JavaPairRDD<CloSTIndex, Iterable<STPoint>> doPartitioning(JavaRDD<Trajectory> trajectoryRDD) {
		SparkCloSTPartitioner clostPartitioner = new SparkCloSTPartitioner(clostParams);
		JavaPairRDD<CloSTIndex, STPoint> pointToPartitionRDD = clostPartitioner
				.doCloSTPartitioning(trajectoryRDD);
		spatialModel = clostPartitioner.getSpatialModel();
		
		// remove from memory
		trajectoryRDD.unpersist();
		
		return pointToPartitionRDD.groupByKey();
	}
	
	/**
	 * Write each partition (key/value pair) in the RDD to disk.
	 * Each partition is saved into a different file.
	 * Name of the file on disk is the partition index.
	 * 
	 * @param partitionsRDD The CloST partitions RDD to write.
	 */
	private void writePartitions(JavaPairRDD<CloSTIndex, Iterable<STPoint>> partitionsRDD) {
		partitionsRDD.foreach(partition -> {
			// save partition to disk as Java object
			final String fileName = partition._1.toString();
			IOService.writeObject(partition, coldPartitionsLocation, fileName);
		});
	}
}
