import java.io.Serializable;

public class PlayerPacket implements Serializable {

	// MazeWar Packet types
	public static final int PLAYER_QUIT = 0;
	public static final int PLAYER_REGISTER_REPLY = 200;
	public static final int PLAYER_REGISTER_UPDATE = 300;
	public static final int PLAYER_REGISTER = 100;
	public static final int PLAYER_FORWARD = 101;
	public static final int PLAYER_BACKUP = 102;
	public static final int PLAYER_LEFT= 103;
	public static final int PLAYER_RIGHT = 104;
	public static final int PLAYER_FIRE = 105;

	// RAFT Packet Types
	public static final int RAFT_VOTE_REQUEST = 400;
	public static final int RAFT_VOTE_REPLY = 401;
	public static final int RAFT_APPEND_ENTRY = 500;
	public static final int RAFT_APPEND_ACK = 501;

	public String hostName;
	public String playerName;
	
	// Unique ID of this player
	public String uID;
	
	// Packet type
	public int type;

	// Port that the client will listen on for broadcasts from the server
	public int listenPort;

	// RAFT RequestVote RPC information
	public int term;
	public int candidateID;
	public int lastLogIndex;
	public int lastLogTerm;
	public Boolean voteGranted;

	// RAFT AppendEntries RPC Information
	public int leaderID;
	public int prevLogIndex;
	public int prevLogTerm;
	// PLACE ARRAY OF LOG ENTRIES HERE

}
