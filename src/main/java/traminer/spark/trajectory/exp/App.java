package traminer.spark.trajectory.exp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import traminer.io.IOService;
import traminer.io.params.SparkParameters;
import traminer.spark.trajectory.clost.CloSTParameters;
import traminer.spark.trajectory.gui.TrajectoryStorageClient;
import traminer.util.spatial.objects.st.STCircle;

public class App {
	/** Data paths */
	private static final String SPARK = "local[*]";		//"spark://spark.master:7077"; 
	private static final String COLD_DATA  = "C:/data/app/cold/";
	private static final String INPUT_DATA = "C:/data/app/trajectory/";
	private static final String QUERY_DATA = "C:/data/app/query/";
	
	/** App services parameters */
	private static SparkParameters sparkParams;
	
	/** Application model/business logic */
	private static TrajectoryStorageClient storageClient;
	
	/** Running services */
	private static boolean isPhisicalPlannerOn = false;
	private static boolean isStorageControllerOn = false;
	private static boolean isTaskSchedulerOn = false;
	
	public static void main(String[] args) {
		// read parameters
		final boolean hotMode = false;
		final long activeTimeWindow = 60000; // milliseconds
		final int maxTasksNumber = 10;
		
		setup();
		try {
			startPhysicalPlanner(hotMode);
			//while (!isPhisicalPlannerOn);
			//startStorageController(activeTimeWindow);
			//while (!isStorageControllerOn);
			//startTaskScheduler(maxTasksNumber);
			//while (!isTaskSchedulerOn);
			System.out.println("*****FINISHED!!!");
		} catch(Exception e) {
			System.out.println("*****ERROR!!!");
		}
		
	}

	private static void setup() {
		// setup Spark access
		sparkParams	= new SparkParameters(SPARK, "TrajectoryStorageApp");
		// setup partitioning parameters boundaries
		double minX = 0.0;
		double minY = 0.0;
		double maxX = 1000.0;
		double maxY = 1000.0;
		long minT = 1;
		long maxT = 200000;
		 		
		// CloST parameters
		long timeWindow   = 10000;
		int nodesCapacity = 1000;
		int sampleSize    = 10000;
				
		// setup CloST parameters
		CloSTParameters clostParams = new CloSTParameters(minX, maxX, minY, maxY, 
				timeWindow, nodesCapacity, sampleSize);
		
		// start business logic - model
		storageClient = new TrajectoryStorageClient(sparkParams, clostParams, COLD_DATA);
	}
	
	private static void startPhysicalPlanner(boolean hotMode) {
		System.out.println("*****Starting physical planner.");

		// Start physical planner in a separated thread
		final int numRDDParts = 1000;
		Runnable process = new Runnable() {
			@Override
			public void run() {
				// start in hot mode
				if (hotMode) {
					isPhisicalPlannerOn = storageClient.startPhysicalPlannerHot(
							INPUT_DATA, numRDDParts);
				} 
				// start in cold mode
				else {
					isPhisicalPlannerOn = storageClient.startPhysicalPlannerCold(
							INPUT_DATA, numRDDParts);
				}
			}
		};
		Thread thread = new Thread(process);
		thread.setDaemon(true);
		thread.start();
	}

	private static void startStorageController(long activeTimeWindow) {
		System.out.println("*****Starting storage controller");
		if (!isPhisicalPlannerOn) {
			System.out.println("*****Phisical Planner is OFF. Start it first!");
		}

		final long scanFrequency = 1000; // milliseconds
		
		// Start Storage Controller in a separated thread
		Runnable process = new Runnable() {
			@Override
			public void run() {
				isStorageControllerOn = storageClient.startStorageController(
						activeTimeWindow, scanFrequency);
			}};
		Thread thread = new Thread(process);
		thread.setDaemon(true);
		thread.start();
	}
	
	private static void startTaskScheduler(int maxTasksNumber) {
		System.out.println("*****Starting task scheduler");
		
		// Start TaskScheduler in a separated thread
		Runnable process = new Runnable() {
			@Override
			public void run() {
				isTaskSchedulerOn = storageClient.startTaskScheduler(maxTasksNumber);
			}};
		Thread thread = new Thread(process);
		thread.setDaemon(true);
		thread.start();
	}
	
	private static List<STCircle> readQuery() {
		System.out.println("*****Reading user queries");
		
		List<STCircle> queryList = new ArrayList<>(10000);
		try {
			List<String> file = IOService.readFile(QUERY_DATA + "query.txt");
						
			for (String line : file) {
				String[] words = line.split(",");
				int x = Integer.parseInt(words[0]);
				int y = Integer.parseInt(words[1]);
				int r = Integer.parseInt(words[2]);
				long ta = Long.parseLong(words[3]);
				long tb = Long.parseLong(words[4]);
				
				queryList.add(new STCircle(x, y, r, ta, tb));
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return queryList;
	}
}
