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
			MazeLeader.commitIndex.set(MazeLeader.actionLog.size());

			while( (pPacket = (PlayerPacket) fromPlayer.readObject()) != null) {

				PlayerPacket cPacket = new PlayerPacket();
				PlayerInfo pInfo = new PlayerInfo();

				// Only need to do fancy things for a registration request. Simply set up the PlayerInfo object
				// to add to the PlayerList and we are good to go
				if(pPacket.RAFTType == PlayerPacket.RAFT_APPEND_REQUEST) {
					
					if(MazeLeader.state.get() != RAFTState.RAFT_LEADER) {
						if(MazeLeader.state.get() != RAFTState.RAFT_INTERMEDIATE) {
							break;
						}
					}

					if(pPacket.type == PlayerPacket.PLAYER_REGISTER) {
						MazeLeader.commitIndex.getAndAdd(1);
						MazeLeader.lastApplied.set(MazeLeader.commitIndex.get());

						System.out.println("Added player " + pPacket.uID);

						// Set up PlayerInfo packet for Leader playerlist
						if(!MazeLeader.playerList.containsKey(pPacket.uID)) {
							System.out.println("Added player " + pPacket.uID);
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
						cPacket.uID = pPacket.uID;

						System.out.println("Registered user: " + pPacket.uID + ", from: " + pPacket.hostName + ":" + pPacket.listenPort );

						// Add request to FIFO, should cause handler thread to wake up
						ClientUpdateHandlerThread.requestLog.put(cPacket);
						MazeLeader.requestLog.put(cPacket);
						MazeLeader.actionLog.put(MazeLeader.commitIndex.get(), cPacket);

						break;
					} else if (pPacket.type == PlayerPacket.PLAYER_QUIT) { 
						MazeLeader.playerList.remove(pPacket.uID);
						MazeLeader.requestLog.put(pPacket);

						break;
					} else {

						MazeLeader.commitIndex.getAndAdd(1);
						MazeLeader.lastApplied.set(MazeLeader.commitIndex.get());
						MazeLeader.requestLog.put(pPacket);

						MazeLeader.actionLog.put(MazeLeader.commitIndex.get(), pPacket);

						break;
					}
				} else if (pPacket.RAFTType == PlayerPacket.RAFT_APPEND_ENTRY) {
					// Reject any append entries from other leaders, I AM THE LEADER
					if(MazeLeader.state.get() == RAFTState.RAFT_LEADER)
						break;
					else if (MazeLeader.state.get() == RAFTState.RAFT_CANDIDATE) {
						// Should be safe to do this, haven't tested thoroughly, but
						// if we are in candidate and someone sent an APPEND_ENTRY, likely
						// they are the rightful leader, so set myself to follower
						MazeLeader.state.set(RAFTState.RAFT_FOLLOWER);
					}
					else if (MazeLeader.state.get() == RAFTState.RAFT_INTERMEDIATE) {
						// If we are in intermediate state and we get an APPEND_ENTRY,
						// we accept it, always. Should only ever receive a join in this case
						pPacket.appendGranted = true;
						MazeLeader.requestLog.put(pPacket);
						break;
					}

					// Check for inconsistencies, and if so, deny the request
					// Usually occurs if the leader is out of date, but this is unlikely
					if(pPacket.term < MazeLeader.currentTerm.get()) {
						System.out.println("Type: " + pPacket.type + " RAFT Type: " + pPacket.RAFTType);
						System.out.println(pPacket.term + " < " + MazeLeader.currentTerm.get());
						pPacket.appendGranted = false;
						MazeLeader.requestLog.put(pPacket);
						break;
					}

					// As above, there are some issues with the term that was last committed at
					// the leader, so we should not accept this
					if(ClientUpdateHandlerThread.actionLog.containsKey(pPacket.prevLogIndex)) {
						if(ClientUpdateHandlerThread.actionLog.get(pPacket.prevLogIndex).term != pPacket.prevLogTerm) {
							System.out.println("Last Log Term: " + ClientUpdateHandlerThread.actionLog.get(pPacket.prevLogIndex).prevLogTerm);
							System.out.println("Prev Log Term: " + pPacket.prevLogTerm);
							pPacket.appendGranted = false;
							MazeLeader.requestLog.put(pPacket);
							break;
						}
					}

					// Otherwise we're good, we can put it in our queue
					pPacket.appendGranted = true;
					MazeLeader.requestLog.put(pPacket);

					// Ensure we have the most up-to-date commitIndex
					if(pPacket.leaderCommit > MazeLeader.commitIndex.get()) {
						if(pPacket.leaderCommit < ClientUpdateHandlerThread.actionLog.size())
							MazeLeader.commitIndex.set(pPacket.leaderCommit);
						else MazeLeader.commitIndex.set(ClientUpdateHandlerThread.actionLog.size());
					}

					break;
				} else {
					if (pPacket.RAFTType == PlayerPacket.RAFT_APPEND_DENY || pPacket.RAFTType == PlayerPacket.RAFT_APPEND_ACCEPT) {
						if(MazeLeader.state.get() == RAFTState.RAFT_LEADER) {
							MazeLeader.requestLog.put(pPacket);
						}
					} else { 
						MazeLeader.requestLog.put(pPacket);
					}

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
