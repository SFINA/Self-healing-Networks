/*
 * Copyright (C) 2015 SFINA Team
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package experiments;

//import core.InterdependentAgent;
import applicationsMain.PowerExchangeAgent;
import core.InterdependentAgentNoEvents;
import interdependent.InterdependentNetwork;
import org.apache.log4j.Logger;
import power.backend.InterpssFlowDomainAgent;
import power.backend.MatpowerFlowDomainAgent;
import protopeer.Experiment;
import protopeer.Peer;
import protopeer.PeerFactory;
import protopeer.SimulatedExperiment;
import protopeer.util.quantities.Time;

/**
 *
 * @author evangelospournaras
 */
public class TestPowerExchange extends SimulatedExperiment{
    
    private static final Logger logger = Logger.getLogger(TestPowerExchange.class);
    
    private final static String expSeqNum="04"; //originally was 03
    
    private static String experimentID="experiment-"+expSeqNum;
    
    //Simulation Parameters
    private final static int bootstrapTime=2000;
    private final static int runTime=1000;
    private final static int runDuration=5; // Number of simulation steps + 2 bootstrap steps, 5 is the one I produced result for
    private final static int N=2; // Number of peerlet
    
    public static double maxCapacity;
    
    
    public static void main(String[] args) {
        
        maxCapacity=15;
        
        if (args.length > 0) {
            try {
                maxCapacity = Integer.parseInt(args[0]);
 
            } catch (NumberFormatException e) {
                System.err.println("Argument" + args[0] + " must be an integer.");
                System.exit(1);
            }
        }
        
        Experiment.initEnvironment();
        TestPowerExchange test = new TestPowerExchange();
        test.init();
        
        PeerFactory peerFactory = new PeerFactory() {
            public Peer createPeer(int peerIndex, Experiment experiment) {
                // First network
                Peer peer = new Peer(peerIndex);
//                if (peerIndex==0){
//                    peer.addPeerlet(new PowerExchangeAgent1(
//                        experimentID, 
//                        Time.inMilliseconds(bootstrapTime),
//                        Time.inMilliseconds(runTime),maxCapacity));
//                }
//                else
                
                
                peer.addPeerlet(new PowerExchangeAgent(
                        experimentID, 
                        Time.inMilliseconds(bootstrapTime),
                        Time.inMilliseconds(runTime),maxCapacity));
                peer.addPeerlet(new InterpssFlowDomainAgent(
                        experimentID, 
                        Time.inMilliseconds(bootstrapTime),
                        Time.inMilliseconds(runTime)));
                return peer;
            }
        };
        test.initPeers(0,N,peerFactory);
        test.startPeers(0,N);
        
        InterdependentNetwork interNetwork = new InterdependentNetwork(N);
        for(Peer peer : test.getPeers()){
            InterdependentAgentNoEvents simuAgent = (InterdependentAgentNoEvents)peer.getPeerletOfType(InterdependentAgentNoEvents.class);
            simuAgent.addInterdependentNetwork(interNetwork);
            interNetwork.addNetworkAddress(peer.getNetworkAddress());
        }
        
        //run the simulation
        test.runSimulation(Time.inSeconds(runDuration));
    }
}

//-Topology of flowNetwork not changing
//-THe next callBackend (in the next iteration) is done on the same flownetwork, not the new changed one
//-This is why there is no advancing in iteration (because for the code it will think the topology did not change as a result of power flows)
//-In only changes as a result of initial tripping of line, as exemplified by the occurence of deactivated node11
//-However, if you run power flow, it runs on original network (not the updated one)
//-Flownetwork only changed by topological deactivation, not by exceeding flow (or at least not updated by exceeding flow)
//-Doesn't go more than 2 iteration even for big networks (now test with the proper flow convergence strategy)
//
//Could be because:
//
//-after link flow exceeds, the line is deacivated (by ypdateOverloadLink).
//-but when you run powerflow for the next iteration (because setNetworkchange senses) it doesnt run the powerflow on updated topology 
//maybe problem with execute all events for next iteration