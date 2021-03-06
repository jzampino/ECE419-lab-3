import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MazeLeader extends Thread {

	// This will keep track of all players in the game
	public static ConcurrentSkipListMap<String, PlayerInfo> playerList = new ConcurrentSkipListMap<String, PlayerInfo>();
	// This is a FIFO of all requests, we are serializing everything once we get it
	public static LinkedBlockingQueue<PlayerPacket> requestLog = new LinkedBlockingQueue<PlayerPacket>();
	// Action Log for RAFT
	public static ConcurrentSkipListMap<Integer, PlayerPacket> actionLog = new ConcurrentSkipListMap<Integer, PlayerPacket>();
	// Unique player ID
	public static int pCount = 0;
	// Number of players required for a game. If the playerList is smaller, we have to wait until it is at least
	// numPlayers long
	public static int numPlayers = 0;
	private static ServerSocket serverSocket = null;
	public static int state;

	public MazeLeader(int socket, int numPlayers) {

		try {
			serverSocket = new ServerSocket(socket);
			state = RAFTState.RAFT_INTERMEDIATE;
			this.numPlayers = numPlayers;
			new MazeLeaderProcessor().start();
		}
		catch (IOException e) {
			System.err.println("ERROR: Could not listen on port!");
			System.exit(-1);
		}
	}

	public void run() {

		//Runtime runtime = Runtime.getRuntime();
		//Thread serverShutdown = new Thread(new MazeServerShutdown(serverSocket));
		//runtime.addShutdownHook(serverShutdown);

		boolean listening = true;

		while (listening) {
			// The above is straightforward, just spawn a thread to add items to the FIFO
			try {
				new MazeLeaderRequestHandler(serverSocket.accept()).start();
			} catch (Exception e) {
			}
		}

		try {
			serverSocket.close();
		} catch (Exception e) {
			System.exit(0);
		}
	}
}
