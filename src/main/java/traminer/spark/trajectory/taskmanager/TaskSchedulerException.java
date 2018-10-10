package traminer.spark.trajectory.taskmanager;

/**
 * Exception for errors during task scheduling.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class TaskSchedulerException extends RuntimeException {
	public TaskSchedulerException() {}
	public TaskSchedulerException(String message, Throwable cause) {
		super(message, cause);
	}
	public TaskSchedulerException(String message) {
		super(message);
	}
}
