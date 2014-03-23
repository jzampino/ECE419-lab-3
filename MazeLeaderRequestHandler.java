import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class MazeLeaderRequestHandler extends Thread {
	
	private Socket socket = null;

	public MazeLeaderRequestHandler(Socket socket) {
		super("MazeLeaderRequestHandler");
		this.socket = socket;
	}

	public void run() {

		try {
			ObjectInputStream fromPlayer = new ObjectInputStream(socket.getInputStream());
			PlayerPacket pPacket;
			int numRequests = MazeLeader.actionLog.size();

			while( (pPacket = (PlayerPacket) fromPlayer.readObject()) != null) {

				PlayerPacket cPacket = new PlayerPacket();
				PlayerInfo pInfo = new PlayerInfo();

				// Only need to do fancy things for a registration request. Simply set up the PlayerInfo object
				// to add to the PlayerList and we are good to go
				if(pPacket.type == PlayerPacket.PLAYER_REGISTER) {
					numRequests++;

					if(!MazeLeader.playerList.containsKey(pPacket.uID)) {
						MazeLeader.pCount++;
						pInfo.hostName = pPacket.hostName;
						pInfo.playerName = pPacket.playerName;
						pInfo.uID = pPacket.uID;
						pInfo.listenPort = pPacket.listenPort;
					}
					else {
						System.err.println("ERROR: Duplicate Register Request! User with pID " + pPacket.uID + " already registered!");
						System.exit(-1);
					}
						
					// Add player to list
					MazeLeader.playerList.put(pPacket.uID, pInfo);

					cPacket = pPacket;
					cPacket.type = PlayerPacket.PLAYER_REGISTER_REPLY;
					//cPacket.uID = MazeLeader.pCount;
					cPacket.uID = pPacket.uID;

					System.out.println("Registered user: " + pPacket.uID + ", from: " + pPacket.hostName + ":" + pPacket.listenPort );

					// Add request to FIFO, should cause handler thread to wake up
					MazeLeader.requestLog.put(cPacket);
					MazeLeader.actionLog.put(numRequests, cPacket);

					break;
				} else if (pPacket.type == PlayerPacket.PLAYER_QUIT) { 
					MazeLeader.playerList.remove(pPacket.uID);

					MazeLeader.requestLog.put(pPacket);

					break;
				} else {

					numRequests++;
					MazeLeader.requestLog.put(pPacket);
					MazeLeader.actionLog.put(numRequests, cPacket);

					break;
				}
			}

			fromPlayer.close();
			socket.close();
		} catch (IOException e) {
				e.printStackTrace();
		} catch (ClassNotFoundException e) {
				e.printStackTrace();
		} catch (InterruptedException e) {
				e.printStackTrace();
		}
	}
}
