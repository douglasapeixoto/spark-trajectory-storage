package traminer.spark.trajectory.clost;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.hadoop.io.WritableComparable;

/**
 * CloST hierarchical 3-level index.
 * We extend the CloST index to store whether the partition 
 * of the index is either hot or cold.
 * 
 * @author douglasapeixoto
 */
@SuppressWarnings("serial")
public class CloSTIndex implements WritableComparable<CloSTIndex>, Serializable {
	/** Object ID index */
	public String oid;
	/** Spatial index - location*/
	public String loc;
	/** Temporal index */
	public String time;
	
	/** Mark if the partition of this index is a hot partitions (hotspot) */
	private boolean isHot = true; // must start hot
	private long activeTime = 0;
	
	/**
	 * Creates a new 3-level index.
	 * 
	 * @param oid Object ID index.
	 * @param loc Object spatial location index.
	 * @param time Object time interval index.
	 */
	public CloSTIndex(String oid, String loc, String time) {
		if (oid == null) {
			throw new NullPointerException("CloST OID cannot be null.");
		}
		if (loc == null) {
			throw new NullPointerException("CloST LOC cannot be null.");
		}
		if (time == null) {
			throw new NullPointerException("CloST TIME cannot be null.");
		}
		this.oid = oid;
		this.loc = loc;
		this.time = time;
	}

	@Override
	public String toString() {
		return (oid + "&" + loc + "&" + time);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((loc == null) ? 0 : loc.hashCode());
		result = prime * result + ((oid == null) ? 0 : oid.hashCode());
		result = prime * result + ((time == null) ? 0 : time.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		CloSTIndex other = (CloSTIndex) obj;
		if (!this.loc.equals(other.loc)) return false;
		if (!this.oid.equals(other.oid)) return false;
		if (!this.time.equals(other.time)) return false;
		return true;
	}

	/**
	 * @return True if this index belongs to a hot partition (hotspot).
	 */
	public boolean isHot() {
		return isHot;
	}
	
	/**
	 * Mark if this index belongs to a hot or cold partition.
	 * @param isHot Whether the partition of this index is 
	 * either hot (true) or cold (false).
	 */
	public void setHot(boolean isHot) {
		this.isHot = isHot;
	}
	
	/**
	 * @return The last time the partition with 
	 * this index became active.
	 */
	public long getActiveTime() {
		return activeTime;
	}

	/**
	 * @param activeTime The last time the partition with 
	 * this index became active.
	 */
	public void setActiveTime(long activeTime) {
		this.activeTime = activeTime;
	}

	@Override
	public void write(DataOutput out) throws IOException {
		// save only the index information
		byte[] oidBytes = oid.getBytes("UTF-8");
		out.writeInt(oidBytes.length);
		out.write(oidBytes);
		
		byte[] locBytes = loc.getBytes("UTF-8");
		out.writeInt(locBytes.length);
		out.write(locBytes);
		
		byte[] timeBytes = time.getBytes("UTF-8");
		out.writeInt(timeBytes.length);
		out.write(timeBytes);
	}
	
	@Override
	public void readFields(DataInput in) throws IOException {
		int oidLength = in.readInt();
		byte[] oidBytes = new byte[oidLength];
		in.readFully(oidBytes);
		oid = new String(oidBytes,"UTF-8");
		
		int locLength = in.readInt();
		byte[] locBytes = new byte[locLength];
		in.readFully(locBytes);
		loc = new String(locBytes,"UTF-8");
		
		int timeLength = in.readInt();
		byte[] timeBytes = new byte[timeLength];
		in.readFully(timeBytes);
		time = new String(timeBytes,"UTF-8");
	}

	@Override
	public int compareTo(CloSTIndex o) {
		if (oid.equals(o.oid)) {
			if (loc.equals(o.loc)) {
				if (time == o.time) {
					return 0;
				} else {
					return time.compareTo(o.time);
				}
			} else {
				return loc.compareTo(o.loc);
			}
		} else {
			return oid.compareTo(o.oid);
		}
	}

}
