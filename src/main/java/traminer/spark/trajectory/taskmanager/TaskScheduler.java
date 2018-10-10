package traminer.spark.trajectory.taskmanager;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.spark.api.java.JavaRDD;

import traminer.io.log.ObservableLog;
import traminer.spark.trajectory.querymanager.QueryProcessor;
import traminer.spark.trajectory.querymanager.BoxQuery;
import traminer.spark.trajectory.querymanager.CircleQuery;
import traminer.util.Printable;
import traminer.util.trajectory.Trajectory;

/**
 * A Singleton service to schedule the execution of user queries. 
 * The Task Scheduler receives and queue a number of N query requests
 * from users, and submit a number of M queries in FIFO order to be 
 * processed concurrently by the Query Manager.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public final class TaskScheduler implements Serializable, Printable {
	/** Amount of time to wait until a new query is submitted 
	 * by the scheduler (in milliseconds). */
	private static final long DELAY = 100;
	
	/** Mark whether the scheduler is already started. */
	private static boolean start = false;
	
	/** The query processor service. */
	private static QueryProcessor queryProcessor;
	
	/** The query scheduler service (concurrent tasks queue). */
	private static ScheduledExecutorService scheduler;
	
	/** Number of queries submitted */
	private int queryCount = 0;
	
	/** App log */
	private ObservableLog log = ObservableLog.instance();
	
	/** A singleton instance of this service */
	private static TaskScheduler instance = null;	
	
	/** Exists only to avoid instantiation. */
	private TaskScheduler() {}
	
	/**
	 * @return A singleton instance of this class.
	 */
	public static TaskScheduler getInstance() {
		if (instance == null) {
			instance = new TaskScheduler();
		}
		return instance;
	}
	
	/**
	 * Start the scheduler with the given configurations.
	 * 
	 * @param maxTasksNumber Maximum number of tasks that can be submitted for 
	 * processing by the scheduler at once (maximum number of concurrent processes).
	 * @param queryProcessor The query processor service.
	 */
	public void start(final int maxTasksNumber,	QueryProcessor queryProcessor) {
		log.info("[TASK-SCHEDULER] Starting task scheduler with (" +maxTasksNumber+ ") "
				+ "maximum concurrent Threads.");
		TaskScheduler.queryProcessor = queryProcessor;
		TaskScheduler.start = true;
		
		// config the executor service
		scheduler = Executors.newScheduledThreadPool(maxTasksNumber);	
	}
	
	/**
	 * Stops the scheduler service.
	 */
	public void stop() {
		log.info("[TASK-SCHEDULER] Stopping Task Scheduler");
		if (scheduler != null) scheduler.shutdown();
	}
	
	/**
	 * @return Whether or not the TaskScheduler is running.
	 */
	public boolean isRunning() {
		return start;
	}
	
	public int queryCount() {
		return queryCount;
	}

	
	/**
	 * Submit a query for scheduled processing.
	 * 
	 * @param query The query to execute.
	 * @return A Future containing a RDD with the query results.
	 */
	public synchronized Future<JavaRDD<Trajectory>> submit(BoxQuery query) throws TaskSchedulerException {
/*		if (start) {
			query.setId(++queryCount);
			// schedule for execution
			Future<JavaRDD<Trajectory>> future = scheduler.schedule(
				       new QueryExecutorTask(query), DELAY, TimeUnit.MILLISECONDS);	
			return future;
		} else {
			String msg = "Unnable to submit requests while "
					+ "TaskScheduler is off. Start it first.";
			log.error("[TASK-SCHEDULER] " + msg);
			throw new TaskSchedulerException(msg);
		}*/
		return null;
	}

	/**
	 * Submit a query for scheduled processing.
	 * 
	 * @param query The query to execute.
	 * @return A Future containing a RDD with the query results.
	 */
	public synchronized Future<JavaRDD<Trajectory>> submit(CircleQuery query) throws TaskSchedulerException {
		if (start) {
			query.setId(++queryCount);
			// schedule for execution
			Future<JavaRDD<Trajectory>> future = scheduler.schedule(
				       new QueryExecutorTask(query), DELAY, TimeUnit.MILLISECONDS);	
			return future;
		} else {
			String msg = "Unnable to submit requests while "
					+ "TaskScheduler is off. Start it first.";
			log.error("[TASK-SCHEDULER] " + msg);
			throw new TaskSchedulerException(msg);
		}
	}
	
	/**
	 * Creates a new task to execute a scheduled query concurrently.
	 * Queries are submitted and executed in a FIFO fashion.
	 */
	private class QueryExecutorTask implements Callable<JavaRDD<Trajectory>> {
		private CircleQuery query;
		
		/**
		 * Creates a execution task for the given query.
		 * @param query The query submitted for execution.
		 */
		public QueryExecutorTask(CircleQuery query){
			this.query = query;
		}
		
		@Override
		public JavaRDD<Trajectory> call() throws Exception {
			// plan the query and execute
			return queryProcessor.processQuery(query);
		}	
	}
}
