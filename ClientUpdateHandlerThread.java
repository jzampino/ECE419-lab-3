import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class ClientUpdateHandlerThread extends Thread {

	private Socket socket = null;
	private Maze maze = null;

	public ClientUpdateHandlerThread(Socket socket, Maze maze) {
		super("ClientUpdateHandler");
		this.socket = socket;
		this.maze = maze;
	}

	// We won't constantly listen. Instead we service the request we get and let
	// the thread exit.
	public void run() {
		try {
			ObjectInputStream fromServer = new ObjectInputStream(socket.getInputStream());

			PlayerPacket pPacket = new PlayerPacket();

			while ( (pPacket = (PlayerPacket) fromServer.readObject()) != null) {

				// New player is joining the game after use, add them to our playerList using their uID
				// as key and add them to the maze. Remote clients are purple.
				if(pPacket.type == PlayerPacket.PLAYER_REGISTER_REPLY || pPacket.type == PlayerPacket.PLAYER_REGISTER_UPDATE) {

					System.out.println("Received Join from Player: " + pPacket.playerName);

					Client newClient = new RemoteClient(pPacket.playerName);

					//ClientUpdateHandler.playerList.put(pPacket.uID, newClient);

					maze.addClient(newClient);

					fromServer.close();
					socket.close();

					break;
				} else if (pPacket.type == PlayerPacket.PLAYER_FORWARD) {
					Client updateClient = ClientUpdateHandler.playerList.get(pPacket.uID);

					// Process a request for ourselves or a remote client. Doesn't matter
					// these methods were just removed from Client.java and placed here. Functions
					// called by GUIClient were changed to facilitate this.
					if(maze.moveClientForward(updateClient)) {
                        updateClient.notifyMoveForward();
                	}

					break;
				} else if (pPacket.type == PlayerPacket.PLAYER_BACKUP) {

					Client updateClient = ClientUpdateHandler.playerList.get(pPacket.uID);

					if(maze.moveClientBackward(updateClient)) {
                        updateClient.notifyMoveBackward();
					}
					break;
                } else if (pPacket.type == PlayerPacket.PLAYER_LEFT) {
					Client updateClient = ClientUpdateHandler.playerList.get(pPacket.uID);

                	updateClient.notifyTurnLeft();

					break;
				} else if (pPacket.type == PlayerPacket.PLAYER_RIGHT) {	
					Client updateClient = ClientUpdateHandler.playerList.get(pPacket.uID);

                	updateClient.notifyTurnRight();

					break;
				} else if (pPacket.type == PlayerPacket.PLAYER_FIRE) {
					Client updateClient = ClientUpdateHandler.playerList.get(pPacket.uID);

					// Not going to handle this in a complex way, as it stands this will simply
					// compute where the projectile will be based on our own counter. Should
					// be sufficient but will need to fix this for Lab 3 (maybe)
					if(maze.clientFire(updateClient)) {
                        updateClient.notifyFire();
					}

					break;
				} else if (pPacket.type == PlayerPacket.PLAYER_QUIT) {

					System.out.println("Player quit..Ending game...");

					if(pPacket.uID == "") {
						Mazewar.quit(1);
					} else {
						Mazewar.quit(0);
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
	}
}
