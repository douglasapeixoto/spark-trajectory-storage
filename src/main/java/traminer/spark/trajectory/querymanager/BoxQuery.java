package traminer.spark.trajectory.querymanager;

import java.io.Serializable;
import java.util.Collection;

import traminer.util.spatial.objects.Rectangle;
import traminer.util.trajectory.Trajectory;

/**
 * A spatial-temporal range query to be submitted
 * for processing.
 * <p>
 * A range-query is a spatial region or spatial object 
 * with a time interval.
 *  
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class BoxQuery implements Serializable {
	/** Query identifier */
	private int queryId;
	/** The spatial region/object of this query */
	private final Rectangle spatialRegion;
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
	public BoxQuery(Rectangle spatialRegion, long timeStart, long timeFinal) {
		this.spatialRegion = spatialRegion;
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
	public Rectangle getSpatialRegion() {
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
	
}
