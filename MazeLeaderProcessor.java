import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.Map;

public class MazeLeaderProcessor extends Thread {

	public void run() {
		boolean queueProcessing = true;

		try {
	  		while(queueProcessing) {
				// This thread basically peeks the head of the FIFO and checks to see if
				// there are any pending requests, if not, it will keep looping.

				PlayerPacket toProcess = (PlayerPacket) MazeLeader.requestLog.peek();

				if(toProcess != null) {
					if(toProcess.RAFTType == PlayerPacket.RAFT_APPEND_REQUEST) {
						if(toProcess.type == PlayerPacket.PLAYER_REGISTER_REPLY) {
							toProcess = (PlayerPacket) MazeLeader.requestLog.take();

							MazeLeader.commitIndex.getAndAdd(1);
							
							toProcess.term = MazeLeader.currentTerm.get();
							toProcess.leaderID = java.net.InetAddress.getLocalHost().getHostName();
							toProcess.prevLogIndex = ClientUpdateHandlerThread.actionLog.size(); 
							toProcess.RAFTType = PlayerPacket.RAFT_APPEND_ENTRY;

							if(ClientUpdateHandlerThread.actionLog.size() == 0)
								toProcess.prevLogTerm = 0;
							else
								toProcess.prevLogTerm = ClientUpdateHandlerThread.actionLog.get(toProcess.prevLogIndex).term;

							toProcess.leaderCommit = MazeLeader.commitIndex.get();
							
							PlayerPacket forLeader = toProcess;
							forLeader.acceptCount = 0;

							// Small check to see if anyone else is already registered
							if (MazeLeader.playerList.size() > 0) {
								updateNewPlayer(toProcess);
						
								broadCastAction(toProcess, 1);

							} else {
								broadCastAction(toProcess, 0);
							}
	
						} else {
							// Check to see if the playerList has numPlayers in it
							if(MazeLeader.playerList.size() >= 2 || toProcess.type == PlayerPacket.PLAYER_QUIT) {
								toProcess = (PlayerPacket) MazeLeader.requestLog.take();
						
								// Initialize the RAFT Data that needs to be provided to the client
								toProcess.term = MazeLeader.currentTerm.get();
								toProcess.leaderID = java.net.InetAddress.getLocalHost().getHostName();
								toProcess.prevLogIndex = ClientUpdateHandlerThread.actionLog.lastKey();
								toProcess.prevLogTerm = ClientUpdateHandlerThread.actionLog.get(toProcess.prevLogIndex).term;
								toProcess.leaderCommit = MazeLeader.commitIndex.get();	
								toProcess.RAFTType = PlayerPacket.RAFT_APPEND_ENTRY;
								
								PlayerPacket forLeader = toProcess;
								forLeader.acceptCount = 0;
								
								// Append the message to our Leader log
								MazeLeader.actionLog.put(MazeLeader.actionLog.size() + 1, forLeader);

								// Broadcast the action to all players
								broadCastAction(toProcess, 0);

								if(MazeLeader.playerList.size() == 0) {
									System.out.println("All players quit, next join will start a new game");
								}
							} else if (MazeLeader.state.get() == RAFTState.RAFT_INTERMEDIATE) {
								// Only hold back on this if we are in Intermediate state, otherwise
								// allow players to quit as they please
								toProcess = (PlayerPacket) MazeLeader.requestLog.take();
								System.out.println("Num Players: " + MazeLeader.numPlayers + " Player List Size: " + MazeLeader.playerList.size());
								System.out.println("Waiting for " + (MazeLeader.numPlayers - MazeLeader.playerList.size()) + " more players, discarding request");
							}
						}
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_APPEND_ENTRY) {
						// Received an APPEND_ENTRY message from the leader, we should check
						// to see if we've marked this as OK to accept, and then reply
						// with the appropriate message to the leader

						MazeLeader.electionTimeout = System.nanoTime();
						Socket leaderSocket = new Socket(toProcess.leaderID, 3040);
						ObjectOutputStream toLeader = new ObjectOutputStream(leaderSocket.getOutputStream());
						

						// We've granted the OK to append this to our local log, send an affirmitive 
						// message to the leader and perform the action
						if(toProcess.appendGranted) {
							System.out.println("Accepting Append Entry");
							ClientUpdateHandlerThread.requestLog.put(toProcess);
							toProcess.RAFTType = PlayerPacket.RAFT_APPEND_ACCEPT;

							toLeader.writeObject(toProcess);

						} else if (!toProcess.appendGranted) {
							// We've found something wrong with the request from the leader
							// We are going to discard this message and send the Leader
							// a decline
							System.out.println("Rejecting Append Entry");
							toProcess.RAFTType = PlayerPacket.RAFT_APPEND_DENY;

							toLeader.writeObject(toProcess);
						}

						if (toProcess.type == PlayerPacket.PLAYER_REGISTER_REPLY || toProcess.type == PlayerPacket.PLAYER_REGISTER_UPDATE) {
	
							PlayerInfo pInfo = new PlayerInfo();

							if(!MazeLeader.playerList.containsKey(toProcess.uID)) {
								pInfo.hostName = toProcess.hostName;
								pInfo.playerName = toProcess.playerName;
								pInfo.uID = toProcess.uID;
								pInfo.listenPort = toProcess.listenPort;

								MazeLeader.playerList.put(toProcess.uID, pInfo);
							}
						}
							
						toLeader.close();
						leaderSocket.close();
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_APPEND_DENY) {
						// Known issue, really shouldn't happen unless by some chance the leader
						// dies, which is unlikely, and thing should not come out of sequence unless
						// something is really wrong, so I'm leaving this unhandled
						toProcess = (PlayerPacket) MazeLeader.requestLog.take();
						System.out.println("Process Term: " + toProcess.term + " My term: " + MazeLeader.currentTerm.get());
						System.out.println("If this happens...oh well, known bug");
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_APPEND_ACCEPT) {
						// We've got an affirmitive from a follower, increment the counter
						// for this action and apply it to our local log if possible,
						// otherwise wait for a new message
						toProcess = (PlayerPacket) MazeLeader.requestLog.take();
						for (Map.Entry<Integer, PlayerPacket> logEntry : MazeLeader.actionLog.entrySet()) {
							PlayerPacket entry = logEntry.getValue();
					
							if(entry.uID.equals(toProcess.uID) && entry.term == toProcess.term && entry.leaderCommit == toProcess.leaderCommit && entry.prevLogTerm == toProcess.prevLogTerm) {
								if(entry.acceptCount >= MazeLeader.playerList.size()/2)
									break;
								else if (entry.acceptCount < MazeLeader.playerList.size()/2) {
									entry.acceptCount++;

									if(entry.acceptCount >= MazeLeader.playerList.size()/2)
										ClientUpdateHandlerThread.requestLog.put(entry);
								}
							}
						}
					} else if (toProcess.type == PlayerPacket.START_GAME) {

						// Special packet that initiates the first election
						// If we receive this, convert to CANDIDATE and send
						// a request to all players in the game
						new MazeLeaderHeartBeatHandler().start();

						toProcess = (PlayerPacket) MazeLeader.requestLog.take();

						MazeLeader.state.set(RAFTState.RAFT_CANDIDATE);
						MazeLeader.currentTerm.getAndAdd(1);
						MazeLeader.electionTimeout = System.nanoTime();
						
						if(Mazewar.leaderName.equals(Mazewar.uID))
							broadCastAction(toProcess, 1);
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_VOTE_BEGIN) {
						// Initiate an election, should only happen if a leader
						// stops responding at some point
						toProcess = (PlayerPacket) MazeLeader.requestLog.take();
						
						toProcess.RAFTType = PlayerPacket.RAFT_VOTE_REQUEST;

						broadCastAction(toProcess, 0);
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_VOTE_REQUEST) {
						// Got a request from someone to vote for them
						toProcess = (PlayerPacket) MazeLeader.requestLog.take();

						Socket leaderSocket = new Socket(toProcess.candidateID, 3040);
						ObjectOutputStream toLeader = new ObjectOutputStream(leaderSocket.getOutputStream());

						// If they have a term less than mine, then they are behind, so I will have to
						// deny them leadership
						if (toProcess.term < MazeLeader.currentTerm.get()) {
							toProcess.term = MazeLeader.currentTerm.get();
							toProcess.voteGranted = false;
							toProcess.RAFTType = PlayerPacket.RAFT_VOTE_REPLY;

							toLeader.writeObject(toProcess);
						} else if (MazeLeader.votedFor == null || toProcess.lastLogIndex >= ClientUpdateHandlerThread.actionLog.size() || MazeLeader.votedFor.equals(toProcess.candidateID)) {
							// Otherwise, if I haven't voted for anyone and they have a log which is at least as up-to-date as me, then
							// I can send them my vote.
							toProcess.term = MazeLeader.currentTerm.get();
							toProcess.voteGranted = true;
							toProcess.RAFTType = PlayerPacket.RAFT_VOTE_REPLY;

							MazeLeader.votedFor = toProcess.candidateID;

							System.out.println("Voted for: " + toProcess.candidateID);

							toLeader.writeObject(toProcess);
						}

						toLeader.close();
						leaderSocket.close();
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_VOTE_REPLY && MazeLeader.state.get() == RAFTState.RAFT_CANDIDATE) {
						// Got a reply to my vote request!
						toProcess = (PlayerPacket) MazeLeader.requestLog.take();

						// They voted for me, good! I can add it to my count and
						// convert to a leader if I have majority vote, or wait
						// for more votes
						if (toProcess.voteGranted) {
							MazeLeader.numVotes.getAndAdd(1);

							if(MazeLeader.numVotes.get() >= MazeLeader.playerList.size()/2) {
								System.out.println("I am now leader: " + Mazewar.uID);

								MazeLeader.state.set(RAFTState.RAFT_LEADER);

								PlayerPacket heartBeat = new PlayerPacket();

								heartBeat.RAFTType = PlayerPacket.RAFT_HEARTBEAT;
								heartBeat.candidateID = java.net.InetAddress.getLocalHost().getHostName();
								heartBeat.term = MazeLeader.currentTerm.get();
								Mazewar.leaderName = java.net.InetAddress.getLocalHost().getHostName();

								MazeLeader.heartBeatTimeout = System.nanoTime();
								broadCastAction(heartBeat, 1);
							}
						}
					} else if (toProcess.RAFTType == PlayerPacket.RAFT_HEARTBEAT) {
						// Received a heartbeat message, I need to check if I should change
						// my leader or not, probably should
						toProcess = (PlayerPacket) MazeLeader.requestLog.take();

						if (MazeLeader.state.get() == RAFTState.RAFT_CANDIDATE || MazeLeader.state.get() == RAFTState.RAFT_FOLLOWER || MazeLeader.state.get() == RAFTState.RAFT_INTERMEDIATE) {
							if(!Mazewar.leaderName.equals(toProcess.candidateID)) {
								System.out.println("Received Heartbeat, Setting Leader: " + toProcess.candidateID);
								Mazewar.leaderName = toProcess.candidateID;
							}

							MazeLeader.state.set(RAFTState.RAFT_FOLLOWER);

							if(MazeLeader.currentTerm.get() != toProcess.term)
								MazeLeader.currentTerm.set(toProcess.term);

							MazeLeader.electionTimeout = System.nanoTime();
						} else if (MazeLeader.state.get() == RAFTState.RAFT_LEADER) {
							broadCastAction(toProcess, 0);
						}

					}
				}
 			}
		} catch (IOException e) {
			if(!queueProcessing)
				e.printStackTrace();
		} catch (InterruptedException e) {
			if(!queueProcessing)
				e.printStackTrace();
		}
	}

	// Either broadcast to everyone (including myself, mode 0), or to everyone except myself (mode 1)
	private void broadCastAction(PlayerPacket pAction, int mode) throws IOException {
		PlayerInfo pInfo = null;

		if (mode == 0) {
			for (Map.Entry<String, PlayerInfo> player : MazeLeader.playerList.entrySet()) {
				pInfo = player.getValue();

				if(!pInfo.uID.equals(Mazewar.uID)) {
					Socket socket = new Socket(pInfo.hostName, pInfo.listenPort);

					ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

					toClient.writeObject(pAction);

					toClient.close();
					socket.close();
				}
			}
		} else if (mode == 1) {
			for (Map.Entry<String, PlayerInfo> player : MazeLeader.playerList.entrySet()) {
				pInfo = player.getValue();

				if(!pInfo.uID.equals(pAction.uID) && !pInfo.uID.equals(Mazewar.uID)) {
					Socket socket = new Socket(pInfo.hostName, pInfo.listenPort);

					ObjectOutputStream toClient = new ObjectOutputStream(socket.getOutputStream());

					toClient.writeObject(pAction);

					toClient.close();
					socket.close();
				}
			}
		}
	}

	// Send update packets to the new player so they have an up-to-date map and place
	// themselves in the correct spot
	private void updateNewPlayer(PlayerPacket pAction) throws IOException {
		PlayerPacket pInfo = null;
		Socket newPlayer = null;
		ObjectOutputStream toNewPlayer = null;
		int prevLogIndex = 0;

					
		newPlayer = new Socket(pAction.hostName, pAction.listenPort);
		toNewPlayer = new ObjectOutputStream(newPlayer.getOutputStream());

		for(Map.Entry<Integer, PlayerPacket> player : MazeLeader.actionLog.entrySet()) {
			pInfo = player.getValue();

			PlayerPacket updatePlayer = new PlayerPacket();
						
			updatePlayer.hostName = pInfo.hostName;
			updatePlayer.playerName = pInfo.playerName;
			updatePlayer.uID = pInfo.uID;
			updatePlayer.term = pInfo.term;
			updatePlayer.leaderID = pInfo.leaderID;
			updatePlayer.prevLogIndex = player.getKey() - 1;
			updatePlayer.leaderCommit = pInfo.leaderCommit;

			if(pInfo.uID == pAction.uID) {
				updatePlayer.type = PlayerPacket.PLAYER_REGISTER_REPLY;
			} else {	
				updatePlayer.type = PlayerPacket.PLAYER_REGISTER_UPDATE;
			}

			toNewPlayer.writeObject(updatePlayer);

			prevLogIndex++;
		}

		toNewPlayer.close();
		newPlayer.close();
	}
}
