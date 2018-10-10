package traminer.spark.trajectory.gui;

import java.io.Serializable;
import java.util.concurrent.Future;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;

import traminer.io.IOService;
import traminer.io.log.ObservableLog;
import traminer.io.params.SparkParameters;
import traminer.spark.trajectory.clost.CloSTIndex;
import traminer.spark.trajectory.clost.CloSTParameters;
import traminer.spark.trajectory.datamanager.DataManagerException;
import traminer.spark.trajectory.datamanager.PhysicalPlanner;
import traminer.spark.trajectory.datamanager.StorageController;
import traminer.spark.trajectory.querymanager.QueryPlanner;
import traminer.spark.trajectory.querymanager.QueryProcessor;
import traminer.spark.trajectory.querymanager.BoxQuery;
import traminer.spark.trajectory.taskmanager.TaskScheduler;
import traminer.spark.trajectory.taskmanager.TaskSchedulerException;
import traminer.util.Printable;
import traminer.util.spatial.objects.st.STPoint;
import traminer.util.trajectory.Trajectory;

/**
 * Client/Model for trajectory storage application functionalities.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class TrajectoryStorageClient implements Serializable, Printable {
	/** Application and environment parameters */
	private SparkParameters sparkParams;
	private CloSTParameters clostParams;
	private final String coldDataLocation;
	
	/** Application components/services */
	private PhysicalPlanner physicalPlanner;
	private StorageController storageController;
	private QueryPlanner queryPlanner;
	private QueryProcessor queryProcessor;
		
	/** RDD with index and hot partitions */
	private JavaPairRDD<CloSTIndex,Iterable<STPoint>> hotRDD;
	
	/** App log */
	private ObservableLog log = ObservableLog.instance();
	
	public TrajectoryStorageClient(
			SparkParameters sparkParams,
			CloSTParameters clostParams,
			String coldDataLocation) {
		this.sparkParams = sparkParams;
		this.coldDataLocation = coldDataLocation;
		this.clostParams = clostParams;
	}
	
	/**
	 * Starts the physical planner with all partitions in-memory after the partitioning.
	 * This option should be used only when there is enough memory available in the
	 * cluster to store the data partitions.
	 * 
	 * @param dataPath Path to trajectory data.
	 * @param numPartitions Number of RDD partitions.
	 * 
	 * @return True is the PhysicalPlanner was stated successfully.
	 */
	public boolean startPhysicalPlannerHot(final String dataPath, int numRDDPatitions) {
		if (numRDDPatitions <= 0) {
			throw new IllegalArgumentException("Number of RDD partitions must be "
					+ "greater than zero.");
		}
		if (!IOService.exists(dataPath)) {
			throw new IllegalArgumentException("The given data path does not exist.");
		}
		log.info("Starting Physical Panner in HOT mode.");
		
  	    // Create and start the physical planner - partitioning
		// CloSTParams should contain the index information after storage controller starts
  	    physicalPlanner = new PhysicalPlanner(sparkParams, clostParams, coldDataLocation);
  	    hotRDD = physicalPlanner.startHot(dataPath, numRDDPatitions);
  	    log.info("Physical Panning completed.");
  	    
  	    return true; // started successfully
	}
	
	public boolean startPhysicalPlannerFromExistingPartitioning(int numRDDPatitions) {
		if (numRDDPatitions <= 0) {
			throw new IllegalArgumentException("Number of RDD partitions must be "
					+ "greater than zero.");
		}
		log.info("Starting Physical Panner in HOT mode.");
		
  	    // Create and start the physical planner - partitioning
		// CloSTParams should contain the index information after storage controller starts
  	    physicalPlanner = new PhysicalPlanner(sparkParams, clostParams, coldDataLocation);
  	    hotRDD = physicalPlanner.startHotFromDisk(numRDDPatitions);
  	    log.info("Physical Panning completed.");
  	    
  	    return true; // started successfully
	}
	
	/**
	 * Starts the physical planner with all partitions on-disk (HDFS) after the partitioning.
	 * This option should be used when the available memory for storing the data partitions
	 * is a constraint.
	 * 
	 * @param dataPath Path to trajectory data.
	 * @param numPartitions Number of RDD partitions.
	 * 
	 * @return True is the PhysicalPlanner was stated successfully.
	 */
	public boolean startPhysicalPlannerCold(final String dataPath, int numRDDPatitions) {
		if (numRDDPatitions <= 0) {
			throw new IllegalArgumentException("Number of RDD partitions must be "
					+ "greater than zero.");
		}
		if (!IOService.exists(dataPath)) {
			throw new IllegalArgumentException("The given data path does not exist.");
		}
		log.info("Starting Physical Panner in COLD mode.");
		
  	    // Create and start the physical planner - partitioning
		// CloSTParams should contain the index information after storage controller starts
  	    physicalPlanner = new PhysicalPlanner(sparkParams, clostParams, coldDataLocation);
  	    hotRDD = physicalPlanner.startCold(dataPath, numRDDPatitions);
  	    log.info("Physical Panning completed.");
  	    
  	    return true; // started successfully
	}

	/**
	 * Set up the StorageController and start active-time service.
	 * 
	 * @param activeTimeWindow The window of time the active-time mechanism 
	 * will work on, that is, the window of time (in milliseconds) after which
	 * an inactive partition will be considered cold, and saved to disk.
	 * 	 * @param scanFrequency How often the timer task will scan for cold 
	 * partitions (in milliseconds).
	 * 
	 * @return True is the StorageController was stated successfully.
	 */
	public boolean startStorageController(final long activeTimeWindow, final long scanFrequency) {
		if (activeTimeWindow <= 0) {
			throw new IllegalArgumentException("Active-Time Window must be "
					+ "greater than zero.");
		}
		if (scanFrequency <= 0) {
			throw new IllegalArgumentException("Scan Frequency must be "
					+ "greater than zero.");
		}
		if (!physicalPlanner.isPhysicalPlanningCompleted()) {
			throw new DataManagerException("Unable to start StorageController while "
					+ "PhysicalPlanning is not completed.");
		}
		log.info("Starting Storage Controller.");
		
		// configure the controller and starts the active-time window mechanism
		storageController = new StorageController(sparkParams, coldDataLocation);
  	    storageController.startTimer(hotRDD, activeTimeWindow, scanFrequency); 
  	    log.info("Storage Controller is running."); 
  	    
  	    return true; // started successfully
	}
  
	/**
	 * Start the TaskScheduler with the given maximum number of concurrent tasks.
	 * 
	 * @param maxTasksNumber Maximum number of tasks that can be submitted for 
	 * processing by the scheduler at once (maximum number of concurrent processes).
	 * 
	 * @return True is the TaskScheduller was stated successfully.
	 * @throws TaskSchedulerException
	 */
	public boolean startTaskScheduler(final int maxTasksNumber) 
			throws TaskSchedulerException {
		// cannot start before storage controller is on
		if (!storageController.isRunning()) {
			throw new TaskSchedulerException("Unnable to start TaskScheduler while "
					+ "StorageController is off. Start it first.");
		}
		log.info("Starting Task Scheduler.");
		
		// Start the query planning and processing services
		queryPlanner = new QueryPlanner(clostParams, physicalPlanner); 
		queryProcessor = new QueryProcessor(queryPlanner, storageController);
		
		// starts the singleton service
		TaskScheduler.getInstance().start(maxTasksNumber, queryProcessor);
		log.info("Task Scheduler is running."); 
		log.info("Ready to submit.");
		
		return true; // started successfully
	}
	
	/**
	 * Submit a query for scheduled processing.
	 * 
	 * @param query The query to execute.
	 * 
	 * @return A Future containing a RDD with the query results.
	 * @throws TaskSchedulerException
	 */
	public Future<JavaRDD<Trajectory>> submitQuery(BoxQuery query) 
			throws TaskSchedulerException {
		log.info("Query Submitted.");
		return TaskScheduler.getInstance().submit(query);
	}
}
