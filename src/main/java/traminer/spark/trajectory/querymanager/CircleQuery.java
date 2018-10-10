package traminer.spark.trajectory.querymanager;

import java.io.Serializable;
import java.util.Collection;

import traminer.util.spatial.objects.Circle;
import traminer.util.trajectory.Trajectory;

@SuppressWarnings("serial")
public class CircleQuery implements Serializable {
	/** Query identifier */
	private int queryId;
	/** The spatial region/object of this query */
	private final Circle spatialRegion;
	/** The time interval of this query */
	private final long timeStart, timeFinal;
	/** The list of trajectories intersecting this query */
	private Collection<Trajectory> queryResult = null;

	/**
	 *  Creates a new Range Query with the given spatial-temporal parameters.
	 *  
	 * @param spatialRegion The spatial region, or spatial object, of this query.
	 * @param timeStart The beginning of the time interval to query.
	 * @param timeFinal The final of the time interval to query.
	 */
	public CircleQuery(Circle spatialRegion, long timeStart, long timeFinal) {
		this.spatialRegion = spatialRegion;
		this.timeStart = timeStart;
		this.timeFinal = timeFinal;
	}
	
	/**
	 * Creates a new Range Query with the given spatial-temporal parameters.
	 * 
	 * @param x
	 * @param y
	 * @param radius
	 * @param timeStart The beginning of the time interval to query.
	 * @param timeFinal The final of the time interval to query.
	 */
	public CircleQuery(double x, double y, double radius, long timeStart, long timeFinal) {
		this.spatialRegion = new Circle(x, y, radius);
		this.timeStart = timeStart;
		this.timeFinal = timeFinal;
	}
		 
	public void setId(int id) {
		this.queryId = id;
	}
	public int getId() {
		return queryId;
	}
	
	/**
	 * @return The spatial region, or spatial object, of this query.
	 */
	public Circle getSpatialRegion() {
		return spatialRegion;
	}
	
	/**
	 * @return The beginning of the time interval of this query.
	 */
	public long getTimeStart() {
		return timeStart;
	}

	/**
	 * @return  The end of the time interval of this query.
	 */
	public long getTimeFinal() {
		return timeFinal;
	}

	/**
	 * @return The list of trajectories which intersect with this query.
	 */
	public Collection<Trajectory> getQueryResult() {
		return queryResult;
	}
	
	/**
	 * @param queryResult The list of trajectories which intersect with this query.
	 */
	public void setQueryResult(Collection<Trajectory> queryResult) {
		this.queryResult = queryResult;
	}
	
	@Override
	public String toString() {
		String s = spatialRegion.x() + "," + spatialRegion.y() + "," + 
				spatialRegion.radius() + "," + timeStart + "," + timeFinal;
		return s;
	}
}
