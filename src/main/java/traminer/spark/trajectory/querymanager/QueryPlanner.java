package traminer.spark.trajectory.querymanager;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

import traminer.spark.trajectory.clost.CloSTIndex;
import traminer.spark.trajectory.clost.CloSTParameters;
import traminer.spark.trajectory.datamanager.PhysicalPlanner;
import traminer.util.spatial.structures.quadtree.QuadTreeModel;

/**
 * The Query Planner uses the index to identify candidate partitions 
 * for the query submitted (from the Task Scheduler). 
 * <p>
 * The Query Planner is responsible for the filter step of the queries
 * processing. It identifies the partitions intersecting with the query 
 * using the CloST index.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class QueryPlanner implements Serializable {
	private final CloSTParameters clostParams;
	private final PhysicalPlanner physicalPlanner;
	/**
	 * Creates a new query planner using the given partitioning parameters.
	 * 
	 * @param clostParams The CloST parameter after partitioning,
	 * containing the QuadTree model, time window, and other index
	 * parameters.
	 */
	public QueryPlanner(CloSTParameters clostParams, PhysicalPlanner physicalPlanner) {
		this.clostParams = clostParams;
		this.physicalPlanner = physicalPlanner;
	}

	/**
	 * Finds the index of the partitions containing the query.
	 * 
	 * @param query A spatial-temporal range query to search
	 * for candidate partitions.
	 * 
	 * @return A set containing the index of the partitions intersecting
	 *  with the given query.
	 */
	public Collection<CloSTIndex> doQueryPlanning(CircleQuery query) {
		// find the index of the spatial regions and time blocks
		// containing the query. Then generates the CloST indexes
		final String oid = "1"; // we do not consider the first level
		final QuadTreeModel spatialModel = physicalPlanner.getSpatialModel();
		final long timeWindow = clostParams.getTimeWindow();
		long firstBlock, lastBlock;
		// spatial regions containing this query - Level 2
		HashSet<String> regionsSet = spatialModel
				.rangeSearch(query.getSpatialRegion());
		if (regionsSet == null) {
			return new HashSet<>(); // nothing to return
		}
		// time blocks containing this query - Level 3
		firstBlock = query.getTimeStart() / timeWindow;
		lastBlock  = query.getTimeFinal() / timeWindow;
		
		// generate and return the indexes
		return generateIndexSet(oid, regionsSet, firstBlock, lastBlock);
	}

	/**
	 * Generates the CloST indexes from the given query information.
	 * 
	 * @param oid The query object ID
	 * @param regionsSet The set of spatial regions that intersect with the query.
	 * @param firstBlock The first time block containing the query.
	 * @param lastBlock The last time block containing the query.
	 * 
	 * @return A set of CloST indexes of the partitions containing the query.
	 */
	private HashSet<CloSTIndex> generateIndexSet(String oid, HashSet<String> regionsSet, long firstBlock, long lastBlock) {
		HashSet<CloSTIndex> indexSet = new HashSet<>();
		for (String loc : regionsSet) {
			for (long time = firstBlock; time <= lastBlock; time++) {
				indexSet.add(new CloSTIndex(oid, ""+loc, ""+time));
			}
		}
		return indexSet;
	}
}
