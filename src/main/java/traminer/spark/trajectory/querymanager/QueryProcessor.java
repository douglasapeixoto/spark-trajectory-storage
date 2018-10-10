package traminer.spark.trajectory.querymanager;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;

import scala.Tuple2;
import traminer.spark.trajectory.clost.CloSTIndex;
import traminer.spark.trajectory.datamanager.StorageController;
import traminer.util.spatial.objects.st.STPoint;
import traminer.util.trajectory.Trajectory;

/**
 * The Query Processor is responsible for the queries refinement step 
 * on the candidate partitions from the QueryPlanner, by running a precise 
 * check in each partition to collect trajectory segments satisfying 
 * each of the M queries. Finally, a post processing step is performed to 
 * merge segments/sub-trajectories by trajectory ID.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class QueryProcessor implements Serializable {
	/** The planner to use for query processing. */
	private final QueryPlanner queryPlanner;
	/** Storage controller containing the data partitions.*/
	private final StorageController storageController;

	/**
	 * Creates a new Query Processor using the given services.
	 * 
	 * @param queryPlanner The planner to use for query processing.
	 * @param storageController Storage controller containing the
	 * data partitions.
	 */
	public QueryProcessor(
			QueryPlanner queryPlanner, 
			StorageController storageController) {
		this.queryPlanner = queryPlanner;
		this.storageController = storageController;
	}

	/**
	 * Process the given list of range queries using the 
	 * given list of candidate partitions.
	 * 
	 * @param query The query to process.
	 * @param candidatePartitionsRDD List of candidate partitions
	 * containing the data to query.
	 * 
	 * @return The RDD containing the query results, or NULL if no result was found.
	 */
	public JavaRDD<Trajectory> processQuery(final CircleQuery query) {
		// do planning and request candidate partitions
		Collection<CloSTIndex> indexList = queryPlanner.doQueryPlanning(query);
		if (indexList == null || indexList.isEmpty()) {
			return null;
		}
		JavaPairRDD<CloSTIndex, Iterable<STPoint>> candidatePartitionsRDD =
				storageController.requestPartitions(indexList);
		
		// search in each candidate partition for trajectory points
		// intersecting the query
		JavaRDD<Trajectory> trajectoryRDD = 
			// map each partition to a list of trajectory points
			// intersecting the query (k,v)=(TrajectoryID,Point)
			candidatePartitionsRDD.flatMapToPair(partition -> {
				// The list of trajectory points in this partition
				Iterable<STPoint> pointsItr = partition._2;
				// The refined trajectory points
				List<Tuple2<String,STPoint>> resultMap = new ArrayList<>();
				for (STPoint point : pointsItr) {
					// refine, check whether this trajectory point 
					// is part of this query result
					if (query.getSpatialRegion().intersects(point) &&
						point.time() >= query.getTimeStart() &&
						point.time() <= query.getTimeFinal()) {
						resultMap.add(new Tuple2<String,STPoint>(
								point.getParentId(),point));
					}
				}
				return resultMap.iterator();
			})
			// do post-processing, group points by trajectory ID
			.groupByKey()
			// build the trajectories and sort
			.map(trajectoryPoint -> {
				String tid = trajectoryPoint._1;
				Trajectory t = new Trajectory(tid);
				for (STPoint p : trajectoryPoint._2) t.add(p);
				t.sort(Trajectory.TIME_COMPARATOR);
				return t;
			});

		return trajectoryRDD;
	}	
}
