import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.Map;

public class ClientUpdateHandlerThread extends Thread {

	//private Socket socket = null;
	private Maze maze = null;

	public static LinkedBlockingQueue<PlayerPacket> requestLog = new LinkedBlockingQueue<PlayerPacket>();
	public static ConcurrentSkipListMap<Integer, PlayerPacket> actionLog = new ConcurrentSkipListMap<Integer, PlayerPacket>();
	public static ConcurrentSkipListMap<String, Client> playerList = new ConcurrentSkipListMap<String, Client>();

	//public ClientUpdateHandlerThread(Socket socket, Maze maze) {
	public ClientUpdateHandlerThread(Maze maze) {
		super("ClientUpdateHandler");
		this.maze = maze;
	}

	// We won't constantly listen. Instead we service the request we get and let
	// the thread exit.
	public void run() {
		try {
			while(true) {

				PlayerPacket pPacket = (PlayerPacket) requestLog.peek();

				if(pPacket != null) {
			
					// New player is joining the game after use, add them to our playerList using their uID
					// as key and add them to the maze. Remote clients are purple.
					if(pPacket.type == PlayerPacket.PLAYER_REGISTER_REPLY || pPacket.type == PlayerPacket.PLAYER_REGISTER_UPDATE) {
						pPacket = (PlayerPacket) requestLog.take();

						if(!Mazewar.existingPlayers.containsKey(pPacket.uID) && !ClientUpdateHandlerThread.playerList.containsKey(pPacket.uID)) {

							System.out.println("Received Join from Player: " + pPacket.playerName);

							Client newClient = new RemoteClient(pPacket.playerName);

							ClientUpdateHandlerThread.playerList.put(pPacket.uID, newClient);

							maze.addClient(newClient);

							ClientUpdateHandlerThread.actionLog.put(pPacket.lastLogIndex+1, pPacket);
						}
					} else if (pPacket.type == PlayerPacket.PLAYER_FORWARD) {
						pPacket = (PlayerPacket) requestLog.take();

						Client updateClient = ClientUpdateHandlerThread.playerList.get(pPacket.uID);

						// Process a request for ourselves or a remote client. Doesn't matter
						// these methods were just removed from Client.java and placed here. Functions
						// called by GUIClient were changed to facilitate this.
						if(maze.moveClientForward(updateClient)) {
       	                	updateClient.notifyMoveForward();
        	        	}

						actionLog.put(pPacket.lastLogIndex+1, pPacket);

					} else if (pPacket.type == PlayerPacket.PLAYER_BACKUP) {
						pPacket = (PlayerPacket) requestLog.take();

						Client updateClient = ClientUpdateHandlerThread.playerList.get(pPacket.uID);

						if(maze.moveClientBackward(updateClient)) {
        	                updateClient.notifyMoveBackward();
						}

						actionLog.put(pPacket.lastLogIndex+1, pPacket);

					} else if (pPacket.type == PlayerPacket.PLAYER_LEFT) {
						pPacket = (PlayerPacket) requestLog.take();

						Client updateClient = ClientUpdateHandlerThread.playerList.get(pPacket.uID);

                		updateClient.notifyTurnLeft();

						actionLog.put(pPacket.lastLogIndex+1, pPacket);

					} else if (pPacket.type == PlayerPacket.PLAYER_RIGHT) {	
						pPacket = (PlayerPacket) requestLog.take();

						Client updateClient = ClientUpdateHandlerThread.playerList.get(pPacket.uID);

                		updateClient.notifyTurnRight();

						actionLog.put(pPacket.lastLogIndex+1, pPacket);

					} else if (pPacket.type == PlayerPacket.PLAYER_FIRE) {
						pPacket = (PlayerPacket) requestLog.take();

						Client updateClient = ClientUpdateHandlerThread.playerList.get(pPacket.uID);

						// Not going to handle this in a complex way, as it stands this will simply
						// compute where the projectile will be based on our own counter. Should
						// be sufficient. Turns out it is.
						if(maze.clientFire(updateClient)) {
                   	    	updateClient.notifyFire();
						}

						actionLog.put(pPacket.lastLogIndex+1, pPacket);

					} else if (pPacket.type == PlayerPacket.PLAYER_QUIT) {
						pPacket = (PlayerPacket) requestLog.take();

						// Only quit the game if we're the last one playing
						if(MazeLeader.playerList.size() < 2) {
							if(pPacket.uID == "") {
								Mazewar.quit(1);
							} else {
								Mazewar.quit(0);
							}
						} else {
							System.out.println("Player quit..");
							maze.removeClient(ClientUpdateHandlerThread.playerList.get(pPacket.uID));
							ClientUpdateHandlerThread.playerList.remove(pPacket.uID);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
