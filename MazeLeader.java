import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class MazeLeader extends Thread {

	// This will keep track of all players in the game
	public static ConcurrentSkipListMap<String, PlayerInfo> playerList = new ConcurrentSkipListMap<String, PlayerInfo>();

	// This is a FIFO of all requests, we are serializing everything once we get it
	public static LinkedBlockingQueue<PlayerPacket> requestLog = new LinkedBlockingQueue<PlayerPacket>();

	// Unique player ID
	public static int pCount = 0;
	
	// Number of players required for a game. If the playerList is smaller, we have to wait until it is at least
	public static int numPlayers = 0;
	private static ServerSocket serverSocket = null;
	public static AtomicInteger state = new AtomicInteger(RAFTState.RAFT_INTERMEDIATE);
	
	// RAFT Persistent State Variables
	public static AtomicInteger currentTerm = new AtomicInteger();
	public static String votedFor = null;
	public static long electionTimeout = 0l;
	public static long heartBeatTimeout = 0l;
	public static AtomicInteger numVotes = new AtomicInteger();

	// Action Log for RAFT
	public static ConcurrentSkipListMap<Integer, PlayerPacket> actionLog = new ConcurrentSkipListMap<Integer, PlayerPacket>();

	// RAFT Volatile State Variables
	public static AtomicInteger commitIndex = new AtomicInteger();
	public static AtomicInteger lastApplied = new AtomicInteger();

	public MazeLeader(int socket, int numPlayers) {

		try {
			serverSocket = new ServerSocket(socket);
			this.numPlayers = numPlayers;
			new MazeLeaderProcessor().start();
		}
		catch (IOException e) {
			System.err.println("ERROR: Could not listen on port!");
			System.exit(-1);
		}
	}

	public void run() {
		boolean listening = true;

		while (listening) {
			// The above is straightforward, just spawn a thread to add items to the FIFO
			try {
				new MazeLeaderRequestHandler(serverSocket.accept()).start();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			serverSocket.close();
		} catch (Exception e) {
			System.exit(0);
		}
	}
}
