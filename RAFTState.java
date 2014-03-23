import java.io.Serializable;

public class RAFTState implements Serializable {
	public static final int RAFT_FOLLOWER = 0;
	public static final int RAFT_CANDIDATE = 1;
	public static final int RAFT_LEADER = 2;
	public static final int RAFT_INTERMEDIATE = -1;
}
