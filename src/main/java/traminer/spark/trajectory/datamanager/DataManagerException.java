package traminer.spark.trajectory.datamanager;

/**
 * Exception for errors from the Data Manager.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class DataManagerException extends RuntimeException {
	public DataManagerException() {}
	public DataManagerException(String message, Throwable cause) {
		super(message, cause);
	}
	public DataManagerException(String message) {
		super(message);
	}
}
