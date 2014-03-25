import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class MazeLeaderHeartBeatHandler extends Thread {
	public void run() {


		Random randomGen = new Random();

		try {
			// Loop forever, we send heartbeats or change ourselves to CANDIDATE state should we
			// fail to receive a heartbeat or APPEND_ENTRY in a specific amount of time
			while(true) {
				if(((System.nanoTime() - MazeLeader.electionTimeout)/1000000 >= (1000 + randomGen.nextInt(4))) && (MazeLeader.state.get() == RAFTState.RAFT_FOLLOWER || MazeLeader.state.get() == RAFTState.RAFT_CANDIDATE)) {
					MazeLeader.state.set(RAFTState.RAFT_CANDIDATE);
					System.out.println("Incrementing term");
					MazeLeader.currentTerm.getAndAdd(1);
					MazeLeader.numVotes.set(0);
					MazeLeader.electionTimeout = System.nanoTime();
				} else if (MazeLeader.state.get() == RAFTState.RAFT_LEADER && (((System.nanoTime() - MazeLeader.heartBeatTimeout)/1000000 >= (100 + randomGen.nextInt(4))))) {
					PlayerPacket heartBeat = new PlayerPacket();

					heartBeat.RAFTType = PlayerPacket.RAFT_HEARTBEAT;
					heartBeat.candidateID = java.net.InetAddress.getLocalHost().getHostName();
					heartBeat.term = MazeLeader.currentTerm.get();

					MazeLeader.requestLog.put(heartBeat);
					MazeLeader.heartBeatTimeout = System.nanoTime();
				} if (MazeLeader.state.get() == RAFTState.RAFT_FOLLOWER && (((System.nanoTime() - MazeLeader.heartBeatTimeout)/1000000 >= (100 + randomGen.nextInt(4))))) {
					MazeLeader.state.set(RAFTState.RAFT_CANDIDATE);
					System.out.println("Incrementing term");
					MazeLeader.currentTerm.getAndAdd(1);
					MazeLeader.numVotes.set(0);
					MazeLeader.electionTimeout = System.nanoTime();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
