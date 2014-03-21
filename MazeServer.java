import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MazeLeader {

	// This will keep track of all players in the game
	public static ConcurrentSkipListMap<Integer, PlayerInfo> playerList = new ConcurrentSkipListMap<Integer, PlayerInfo>();
	// This is a FIFO of all requests, we are serializing everything once we get it
	public static LinkedBlockingQueue<PlayerPacket> requestLog = new LinkedBlockingQueue<PlayerPacket>();
	// Unique player ID
	public static int pCount = 0;
	// Number of players required for a game. If the playerList is smaller, we have to wait until it is at least
	// numPlayers long
	public static int numPlayers = 0;
	private static ServerSocket serverSocket = null;

	public static MazeLeader(int socket) {

		boolean listening = true;

		try {
			serverSocket = new ServerSocket(socket);
			new MazeLeaderProcessor().start();
		}
		catch (IOException e) {
			System.err.println("ERROR: Could not listen on port!");
			System.exit(-1);
		}
	}

	public void run() {

		Runtime runtime = Runtime.getRuntime();
		Thread serverShutdown = new Thread(new MazeServerShutdown(serverSocket));
		runtime.addShutdownHook(serverShutdown);

		while (listening) {
			// The above is straightforward, just spawn a thread to add items to the FIFO
			try {
				new MazeLeaderRequestHandler(serverSocket.accept()).start();
			} catch (SocketException e) {
			}
		}

		try {
			serverSocket.close();
		} catch (SocketException e) {
			System.exit(0);
		}
	}
}

class MazeLeaderShutdown implements Runnable {

	private ServerSocket serverSocket;

	public MazeLeaderShutdown (ServerSocket serverSocket) {
		this.serverSocket = serverSocket;
	}

	@Override
	public void run() {
		System.out.println("Shutting down server, sending disconnect to all players");

		PlayerInfo pInfo = new PlayerInfo();
		PlayerPacket pAction = new PlayerPacket();

		pAction.type = PlayerPacket.PLAYER_QUIT;
		pAction.uID = -1;

		try {
			for (Map.Entry<Integer, PlayerInfo> player : MazeLeader.playerList.entrySet()) {
				pInfo = player.getValue();

				Socket socket = new Socket(pInfo.hostName, pInfo.listenPort);

				ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

				toClient.writeObject(pAction);

				toClient.close();
				socket.close();
			}

			serverSocket.close();
		} catch (IOException e) {
		}
	}
}

