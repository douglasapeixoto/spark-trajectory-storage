package traminer.spark.trajectory.datamanager;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;

import scala.Tuple2;

import traminer.io.IOService;
import traminer.io.log.ObservableLog;
import traminer.io.params.SparkParameters;
import traminer.io.spark.SparkContextClient;
import traminer.spark.trajectory.clost.CloSTIndex;
import traminer.util.spatial.objects.st.STPoint;

/**
 * The Storage controller is a workload-aware component responsible 
 * to manage the storage level of the data partitions in the distributed 
 * file system, that is, it controls which data partitions are stored in 
 * main-memory (RDD) and which partitions are stored on disk (HDFS). 
 * <p>
 * Data partitions are exchanged between memory and disk on request of 
 * the Storage Controller based on the query workload and the Sliding
 * Time Window mechanism.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class StorageController implements Serializable {
	private final SparkParameters sparkParams;
	private final String coldPartitionsLocation;
	
	/** Time window after which a hot partition will be considered as cold (in milliseconds) */
	private long activeTimeWindow;
	
	/** Time delay to consider cold partitions - delay caused by 
	 * the partitioning (physical planning) */
	private long timeDelay;
	
	/** Tells whether this service is already running */
	private boolean isRunning = false;
	
	/** Hot partitions only (always in-memory). Cold partitions should be on disk. */
	private JavaPairRDD<CloSTIndex, Iterable<STPoint>> hotPartitionsRDD = null;

	/** App log */
	private ObservableLog log = ObservableLog.instance();
	
	/**
	 * Set up a new Storage Controller with the given parameters.
	 * 
	 * @param sparkParams Spark cluster access parameters.
	 * @param hdfsParams HDFS access parameters.
	 */
	public StorageController(SparkParameters sparkParams, String coldPartitionsLocation) {
		this.sparkParams = sparkParams;
		this.coldPartitionsLocation = coldPartitionsLocation + "\\";	
	}
	
	/**
	 * @return True if this this service is already started, that is,
	 * if the Active-Time mechanism is already running.
	 */
	public boolean isRunning() {
		return isRunning;
	}

	/**
	 * Start the timer service with the initial RDD containing the Hot partitions.
	 * 
	 * @param hotPartitionsRDD The partitions containing the hot data, from
	 * the physical planner, or an empty RDD if the physical planner started
	 * in cold mode. 
	 * @param activeTimeWindow The window of time the active-time mechanism 
	 * will work on, that is, the window of time (in milliseconds) after which
	 * an inactive partition will be considered cold, and saved to disk.
	 * @param scanFrequency How often the timer task will scan for cold 
	 * partitions (in milliseconds).
	 */
	public void startTimer(JavaPairRDD<CloSTIndex, Iterable<STPoint>> hotPartitionsRDD, 
			final long activeTimeWindow, final long scanFrequency) {
		log.info("[STORAGE-CONTROLLER] Starting storage controller with "
				+ "Active-Time window = (" + activeTimeWindow + "ms)");
		this.hotPartitionsRDD = hotPartitionsRDD;
		this.activeTimeWindow = activeTimeWindow;

		// calculate the delay
		long timeStart  = System.currentTimeMillis();
		long activeTime = hotPartitionsRDD.first()._1.getActiveTime();
		this.timeDelay = timeStart - activeTime;
		
		// start the sliding time service
		Timer timer = new Timer();
		timer.schedule(new StorageControllerTimerTask(), 0, scanFrequency);
		
		this.isRunning = true;
	}

	/**
	 * Retrieve all partitions which the index is in the given list.
	 * If the required partitions are cold this method load the
	 * partitions from disk and updates the status of the partitions.
	 * 
	 * @param indexList List of partitions index to retrieve.
	 * @return RDD containing the requested partitions in-memory.
	 */
	public JavaPairRDD<CloSTIndex, Iterable<STPoint>> requestPartitions(
			Collection<CloSTIndex> indexList) {
		// check which of the required partitions are cold, 
		// hence need to be loaded from disk (if any)
		List<CloSTIndex> coldPartitionsToLoad =
			hotPartitionsRDD.keys().filter(partition -> {
				// filter cold partitions
				for (CloSTIndex i : indexList) {
					if (i.equals(partition)) {
						return !partition.isHot();
					}	
				}
				return false;
			}).collect();
		
		// Load the requested cold partitions from disk (if any)
		if (!coldPartitionsToLoad.isEmpty()) {		
			JavaPairRDD<CloSTIndex, Iterable<STPoint>> coldPartitionsRDD = 
					loadPartitionsFromDisk(coldPartitionsToLoad);
			// Join the cold RDD to with the hot RDD
			hotPartitionsRDD = hotPartitionsRDD
				.leftOuterJoin(coldPartitionsRDD)
				.mapValues(joinPair -> joinPair._2().or(joinPair._1()));
		}
		long count = hotPartitionsRDD.count();
		log.info("[STORAGE-CONTROLLER] Hot partitions: " + count);
		
		// filter the requested partitions		
		return hotPartitionsRDD.filter(partition -> 
				indexList.contains(partition._1));
	}

	/**
	 * Load all partitions in the given index list from the disk.
	 * 
	 * @param patitionsIndexList A list of partitions index to load from disk.
	 * @return A RDD with the required partitions.
	 */
	@SuppressWarnings("unchecked")
	private JavaPairRDD<CloSTIndex, Iterable<STPoint>> loadPartitionsFromDisk(
			List<CloSTIndex> patitionsIndexList) {
		// load and map each required index to its partition
		JavaSparkContext sc = SparkContextClient.getJavaContextInstance(sparkParams);
		JavaRDD<CloSTIndex> patitionsIndexRDD = sc.parallelize(patitionsIndexList);
		final long activeTime = System.currentTimeMillis();
		// map each index to the partition of the index (loaded from disk)
		JavaPairRDD<CloSTIndex, Iterable<STPoint>> coldPartitionsRDD = 
			patitionsIndexRDD.mapToPair(index -> {
				Tuple2<CloSTIndex, Iterable<STPoint>> partition = null;
				try {
					String indexPath = coldPartitionsLocation + index.toString();
					log.info("[STORAGE-CONTROLLER] Loading partition: " + index);				
					partition = (Tuple2<CloSTIndex, Iterable<STPoint>>) 
							IOService.readObject(indexPath);
					// mark partition as hot, and set time
					partition._1.setHot(true);
					partition._1.setActiveTime(activeTime);
				} catch (IOException e) {
					log.error("[STORAGE-CONTROLLER] Partition '" + index + 
							"' cannot be found." );							
					e.printStackTrace();
				}
				return partition;
			});
		
		return coldPartitionsRDD;
	}

	/**
	 * Implements the Active-Time window Mechanism.
	 * <p>
	 * Runs the process in this class once every 'timeFadingWindow' milliseconds.
	 * <p>
	 * Scans the RDD for cold partitions, write the cold partitions to the HDFS, 
	 * and remove the cold partitions from the HotPartitions RDD.
	 */
	private class StorageControllerTimerTask extends TimerTask implements Serializable {
		private boolean inProgress = false;
		
		@Override
		public void run() {
			final long currentTime = System.currentTimeMillis();
			log.info("[STORAGE-CONTROLLER] Scanning for cold partitions at: " + currentTime + "ms.");

			// previous scan is still in progress
			if (inProgress) {return;}
					
			// scan the RDD for cold partitions, save cold partitions to disk
			inProgress = true;
			hotPartitionsRDD = hotPartitionsRDD.mapToPair(partition -> {
				CloSTIndex index = partition._1;
				// if partition became cold
				if (currentTime >= (index.getActiveTime() + activeTimeWindow + timeDelay)) {
					// avoid writing while the previous thread/task is not finished yet
					if (index.isHot()) {
						// No need to save cold partitions to disk, they are already there by default
						// mark as cold and empty the partition (must create new one)
						index = new CloSTIndex(index.oid, index.loc, index.time);
						index.setHot(false);
						partition = new Tuple2<CloSTIndex, Iterable<STPoint>>(index, new ArrayList<>(0));
					}
				}
				
				return partition;
			});	
			// get number of hot partitions
			//hotPartitionsCounter = hotPartitionsRDD.keys()
			//		.filter(index -> index.isHot()).count();
			
			// finished
			inProgress = false;
		}
	}
}
