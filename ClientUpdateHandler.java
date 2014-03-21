import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ClientUpdateHandler extends Thread {

	Maze maze = null;
	int listenPort;
	public static ConcurrentSkipListMap<Integer, Client> playerList = new ConcurrentSkipListMap<Integer, Client>();

	public ClientUpdateHandler (Maze maze, int listenPort) {
		super("ClientUpdateHandler");
		this.maze = maze;
		this.listenPort = listenPort;
	}

	public void run() {

		try {
			ServerSocket socket = new ServerSocket(this.listenPort);

			boolean listening = true;

			// Pretty straight forward, spawn a thread each time we receive an
			// update from the server, make sure we pass in the maze
			while(listening) {
				new ClientUpdateHandlerThread(socket.accept(), maze).start();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
