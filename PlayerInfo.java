import java.io.Serializable;

public class PlayerInfo implements Serializable {
	public String hostName;
	public String playerName;
	public String uID;
	public int listenPort;

	// Volatile states for RAFT leaders only
	public int nextIndex;
	public int matchIndex;
}
