/*
Copyright (C) 2004 Geoffrey Alan Washburn
   
This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.
   
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
   
You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307,
USA.
*/
  
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JOptionPane;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import javax.swing.BorderFactory;
import java.io.Serializable;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.*;

/**
 * The entry point and glue code for the game.  It also contains some helpful
 * global utility methods.
 * @author Geoffrey Washburn &lt;<a href="mailto:geoffw@cis.upenn.edu">geoffw@cis.upenn.edu</a>&gt;
 * @version $Id: Mazewar.java 371 2004-02-10 21:55:32Z geoffw $
 */

public class Mazewar extends JFrame {

		/**
		 * Unique ID for this player in the system
		 */
		public static String uID;
		public static int leaderPort;
		public static String leaderName;
		public static ConcurrentSkipListMap<String, Client> existingPlayers = new ConcurrentSkipListMap<String, Client>();

        /**
         * The default width of the {@link Maze}.
         */
        private final int mazeWidth = 20;

        /**
         * The default height of the {@link Maze}.
         */
        private final int mazeHeight = 10;

        /**
         * The default random seed for the {@link Maze}.
         * All implementations of the same protocol must use 
         * the same seed value, or your mazes will be different.
         */
        private final int mazeSeed = 42;

        /**
         * The {@link Maze} that the game uses.
         */
        private Maze maze = null;

        /**
         * The {@link GUIClient} for the game.
         */
        private GUIClient guiClient = null;

        /**
         * The panel that displays the {@link Maze}.
         */
        private OverheadMazePanel overheadPanel = null;

        /**
         * The table the displays the scores.
         */
        private JTable scoreTable = null;
        
        /** 
         * Create the textpane statically so that we can 
         * write to it globally using
         * the static consolePrint methods  
         */
        private static final JTextPane console = new JTextPane();
      
        /** 
         * Write a message to the console followed by a newline.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrintLn(String msg) {
                console.setText(console.getText()+msg+"\n");
        }
        
        /** 
         * Write a message to the console.
         * @param msg The {@link String} to print.
         */ 
        public static synchronized void consolePrint(String msg) {
                console.setText(console.getText()+msg);
        }
        
        /** 
         * Clear the console. 
         */
        public static synchronized void clearConsole() {
           console.setText("");
        }
        
        /**
         * Static method for performing cleanup before exiting the game.
         */
        public static void quit(int mode) {
                // Put any network clean-up code you might have here.
                // (inform other implementations on the network that you have 
                //  left, etc.)
			// Mode 0 if a client is disconnection, Mode 1 if the server was killed
            if (mode == 0) { 
				try {
					PlayerPacket byePacket = new PlayerPacket();

					byePacket.type = PlayerPacket.PLAYER_QUIT;
					byePacket.RAFTType = PlayerPacket.RAFT_APPEND_REQUEST;
					byePacket.uID = uID;

					Socket socket = new Socket(leaderName, leaderPort);
					ObjectOutputStream toLeader = new ObjectOutputStream(socket.getOutputStream());

					toLeader.writeObject(byePacket);

					toLeader.close();
					socket.close();

					while(true) {
						if((MazeLeader.playerList.size() == 0) || !leaderName.equals(java.net.InetAddress.getLocalHost().getHostName()))
                			System.exit(0);
					}
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(0);
				}
			} else if (mode == 1) {
				System.exit(0);
			}
        }
       
        /** 
         * The place where all the pieces are put together. 
         */
        public Mazewar(String leaderName, int numPlayers) {
                super("ECE419 Mazewar");
                consolePrintLn("ECE419 Mazewar started!");
                
				// Set up globals for server communication
				this.leaderName = leaderName;
				this.leaderPort = 3040; // WE WILL ALWAYS USE THIS PORT, FOR SIMPLICITY

				try {
					this.uID = java.net.InetAddress.getLocalHost().getHostName() + "." + leaderPort;
				} catch (UnknownHostException e) {
					System.err.println("Unable to verify hostname!");
					System.exit(1);
				}

                // Create the maze
                maze = new MazeImpl(new Point(mazeWidth, mazeHeight), mazeSeed);
                assert(maze != null);
                
                // Have the ScoreTableModel listen to the maze to find
                // out how to adjust scores.
                ScoreTableModel scoreModel = new ScoreTableModel();
                assert(scoreModel != null);
                maze.addMazeListener(scoreModel);
                
                // Throw up a dialog to get the GUIClient name.
                String name = JOptionPane.showInputDialog("Enter your name");
                if((name == null) || (name.length() == 0)) {
                  Mazewar.quit(1);
                }
                
                // You may want to put your network initialization code somewhere in
                // here.

				String tempID = null; // This isn't necessary anymore, but I don't want to change it
				
				
				try {
					
					// Check if the name of the name of the leader machine passed in is our own
					// if so, we are the first player in the game.
					if((leaderName.equals(java.net.InetAddress.getLocalHost().getHostName()))) {
						// If we are acting leader, we need to receive all requests from the
						// players that want to play (up to numPlayers)
						System.out.println("Mazewar starting. Localhost acting as preliminary leader");

						// create ourselves
                		guiClient = new GUIClient(name);
                		maze.addClient(guiClient);
                		this.addKeyListener(guiClient);

						// Put a register APPEND_ENTRY into our actionLog at the MazeLeader
						PlayerPacket pAction = new PlayerPacket();
						pAction.hostName = leaderName;
						pAction.playerName = name;
						pAction.uID = uID;
						pAction.type = PlayerPacket.PLAYER_REGISTER_UPDATE;
						pAction.prevLogIndex = 1;

						// Set up our own entry into the MazeLeader playerList and
						// ClientUpdateHandlerThread playerList
						PlayerInfo pInfo = new PlayerInfo();
						pInfo.hostName = leaderName;
						pInfo.playerName = name;
						pInfo.uID = uID;
						pInfo.listenPort = leaderPort;

						// Start the log handlers to update other players
						// as well as our own game GUI
						new MazeLeader(leaderPort, numPlayers).start();
						new ClientUpdateHandlerThread(maze).start();

						ClientUpdateHandlerThread.playerList.put(uID, guiClient);
						MazeLeader.playerList.put(pInfo.uID, pInfo);
						MazeLeader.actionLog.put(pAction.prevLogIndex, pAction);

						// Wait for everyone to join
						while(MazeLeader.actionLog.size() <= numPlayers) {
							if(MazeLeader.actionLog.size() == numPlayers) {
								break;
							}
						}
					} else {

						ConcurrentSkipListMap<Integer, PlayerPacket> actionLog = new ConcurrentSkipListMap<Integer, PlayerPacket>();
						ConcurrentSkipListMap<String, PlayerInfo> playerList = new ConcurrentSkipListMap<String, PlayerInfo>();

						Socket sendSocket = new Socket(leaderName, leaderPort);

						PlayerPacket cRequest = new PlayerPacket();
						PlayerPacket cResponse;

						// Set up the initial registration packet for the fake leader,
						// we will block on this until we recevie a confirmation
						// reply from the server
						cRequest.RAFTType = PlayerPacket.RAFT_APPEND_REQUEST;
						cRequest.leaderID = leaderName;
						cRequest.type = PlayerPacket.PLAYER_REGISTER;
						cRequest.hostName = java.net.InetAddress.getLocalHost().getHostName();
						cRequest.playerName = name;
						cRequest.listenPort = leaderPort;
						cRequest.uID = uID;

						ObjectOutputStream toLeader = new ObjectOutputStream(sendSocket.getOutputStream());

						toLeader.writeObject(cRequest);
						toLeader.close();
						sendSocket.close();
	
						ServerSocket listenSocket = new ServerSocket(leaderPort);
						Socket receiveSocket = listenSocket.accept();
						ObjectInputStream fromLeader = new ObjectInputStream(receiveSocket.getInputStream());

						while( ((cResponse = (PlayerPacket) fromLeader.readObject()) != null)) {

							// This particular type of packet is only sent to players
							// who are not the very first player. This ensures that the
							// random number generator places everyone in the same positions
							// on the map. We basically draw all players in the order
							// that they joined based on the order their requests arrive
							// at the fake leader
							if (cResponse.type == PlayerPacket.PLAYER_REGISTER_UPDATE) {
								Client newClient = new RemoteClient(cResponse.playerName);
								maze.addClient(newClient);

								// Add the client to our playerList so we can keep track
								// of them when we update the map in the future
								System.out.println("Added player: " + cResponse.playerName + " from:" + cResponse.uID );
							 	existingPlayers.put(cResponse.uID, newClient);
								actionLog.put((cResponse.prevLogIndex + 1), cResponse);

								if(!MazeLeader.playerList.containsKey(cResponse.uID)) {
									PlayerInfo pInfo = new PlayerInfo();
									pInfo.hostName = cResponse.hostName;
									pInfo.playerName = cResponse.playerName;
									pInfo.uID = cResponse.uID;
									pInfo.listenPort = cResponse.listenPort;
									playerList.put(cResponse.uID, pInfo);
								}

							} else if (cResponse.type == PlayerPacket.PLAYER_REGISTER_REPLY) {
								cResponse.type = PlayerPacket.PLAYER_REGISTER_UPDATE;
								actionLog.put((cResponse.prevLogIndex + 1), cResponse);
							
								if(!MazeLeader.playerList.containsKey(cResponse.uID)) {
									PlayerInfo pInfo = new PlayerInfo();
									pInfo.hostName = cResponse.hostName;
									pInfo.playerName = cResponse.playerName;
									pInfo.uID = cResponse.uID;
									pInfo.listenPort = cResponse.listenPort;
									playerList.put(cResponse.uID, pInfo);
								}
								
								break;
							}
						}
						
						fromLeader.close();
						receiveSocket.close();
						listenSocket.close();

						// Spawn a thread to handle broadcasted updates from the leader
						new MazeLeader(leaderPort, numPlayers).start();
						new ClientUpdateHandlerThread(maze).start();
					
						// Add received data to our MazeLeader and Client logs
						for (Map.Entry<Integer, PlayerPacket> action : actionLog.entrySet()) {
							MazeLeader.actionLog.put(action.getKey(), action.getValue());
						}

						for (Map.Entry<String, PlayerInfo> pData : playerList.entrySet()) {
							MazeLeader.playerList.put(pData.getKey(), pData.getValue());
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
					System.err.println("ERROR: Issue binding to server socket!");
					System.exit(-1);
				} catch (ClassNotFoundException e) {
					e.printStackTrace();
					System.exit(-1);
				}

				if(tempID == "") {
					System.err.println("ERROR: Issue identifying player with remote server!");
					System.exit(-1);
				}

				System.out.println("Local Player " + name + " registered.");

                // Create the GUIClient and connect it to the KeyListener queue

				try {
					if(!leaderName.equals(java.net.InetAddress.getLocalHost().getHostName())) {
                		guiClient = new GUIClient(name);
                		maze.addClient(guiClient);
                		this.addKeyListener(guiClient);
						existingPlayers.put(uID, guiClient);
					} else {
						// If we are the leader (fake one), we need to initiate the vote
						// sequence. Likely what will happen is that we will remain leader
						// indefinitely.
						MazeLeader.currentTerm.getAndAdd(1);
						PlayerPacket mazeStart = new PlayerPacket();
						mazeStart.type = PlayerPacket.START_GAME;
						mazeStart.leaderID = uID;
						MazeLeader.requestLog.put(mazeStart);
						
						PlayerPacket beginVote = new PlayerPacket();

						// Packet to initiate the vote
						beginVote.candidateID = java.net.InetAddress.getLocalHost().getHostName();
						beginVote.RAFTType = PlayerPacket.RAFT_VOTE_BEGIN;
						beginVote.term = MazeLeader.currentTerm.get();
						beginVote.lastLogIndex = ClientUpdateHandlerThread.actionLog.size();
						beginVote.lastLogTerm = ClientUpdateHandlerThread.actionLog.get(beginVote.lastLogIndex).term;

						MazeLeader.requestLog.put(beginVote);
		
						// Self voting packet
						PlayerPacket selfVote = beginVote;
		
						selfVote.RAFTType = PlayerPacket.RAFT_VOTE_REPLY;
						selfVote.voteGranted = true;
						selfVote.term = MazeLeader.currentTerm.get();

						MazeLeader.requestLog.put(selfVote);

						MazeLeader.electionTimeout = System.nanoTime();

						new MazeLeaderHeartBeatHandler().start();
					}
				} catch (Exception e) {
					e.printStackTrace();
					System.exit(-1);
				}

				for (Map.Entry<String, Client> rPlayer : existingPlayers.entrySet()) {
					ClientUpdateHandlerThread.playerList.put(rPlayer.getKey(), rPlayer.getValue());
				}

                // Use braces to force constructors not to be called at the beginning of the
                // constructor.
                /*{
                        maze.addClient(new RobotClient("Norby"));
                        maze.addClient(new RobotClient("Robbie"));
                        maze.addClient(new RobotClient("Clango"));
                        maze.addClient(new RobotClient("Marvin"));
                }*/

                // Create the panel that will display the maze.
                overheadPanel = new OverheadMazePanel(maze, guiClient);
                assert(overheadPanel != null);
                maze.addMazeListener(overheadPanel);
                
                // Don't allow editing the console from the GUI
                console.setEditable(false);
                console.setFocusable(false);
                console.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder()));
               
                // Allow the console to scroll by putting it in a scrollpane
                JScrollPane consoleScrollPane = new JScrollPane(console);
                assert(consoleScrollPane != null);
                consoleScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Console"));
                
                // Create the score table
                scoreTable = new JTable(scoreModel);
                assert(scoreTable != null);
                scoreTable.setFocusable(false);
                scoreTable.setRowSelectionAllowed(false);

                // Allow the score table to scroll too.
                JScrollPane scoreScrollPane = new JScrollPane(scoreTable);
                assert(scoreScrollPane != null);
                scoreScrollPane.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Scores"));
                
                // Create the layout manager
                GridBagLayout layout = new GridBagLayout();
                GridBagConstraints c = new GridBagConstraints();
                getContentPane().setLayout(layout);
                
                // Define the constraints on the components.
                c.fill = GridBagConstraints.BOTH;
                c.weightx = 1.0;
                c.weighty = 3.0;
                c.gridwidth = GridBagConstraints.REMAINDER;
                layout.setConstraints(overheadPanel, c);
                c.gridwidth = GridBagConstraints.RELATIVE;
                c.weightx = 2.0;
                c.weighty = 1.0;
                layout.setConstraints(consoleScrollPane, c);
                c.gridwidth = GridBagConstraints.REMAINDER;
                c.weightx = 1.0;
                layout.setConstraints(scoreScrollPane, c);
                                
                // Add the components
                getContentPane().add(overheadPanel);
                getContentPane().add(consoleScrollPane);
                getContentPane().add(scoreScrollPane);
                
                // Pack everything neatly.
                pack();

                // Let the magic begin.
                setVisible(true);
                overheadPanel.repaint();
                this.requestFocusInWindow();
        }
        
        /**
         * Entry point for the game.  
         * @param args Command-line arguments.
         */
        public static void main(String args[]) {

			/* Create the GUI */
			if(args.length != 2) {
				System.out.println("Invalid number of arguments passed in!");
				System.out.println("Usage: java Mazwar <leaderName> <numPlayers>");
				System.exit(0);
			} else
				if(!args[1].equals("2"))
            		new Mazewar(args[0], Integer.parseInt(args[1]));
				else {
					System.out.println("ERROR: Due to an unknown bug, there must be 3 or more players!");
					System.exit(0);
				}
        }
}
