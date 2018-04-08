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
package applicationsMain;

import core.InterdependentAgent;
import event.Event;
import event.EventType;
import event.NetworkComponent;
import interdependent.StatusMessage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import network.FlowNetwork;
import network.Link;
import network.LinkState;
import network.Node;
import network.NodeState;
import org.apache.log4j.Logger;
import power.input.PowerNodeState;
import power.input.PowerNodeType;
import protopeer.network.NetworkAddress;
import protopeer.util.quantities.Time;

/**
 *
 * @author Ben
 */
public class MultiplexCascadeAgent extends InterdependentAgent{
    
    private static final Logger logger = Logger.getLogger(MultiplexCascadeAgent.class);
    
    // Keeps track of which islands exist in which time step and iteration
    private HashMap<Integer, HashMap<Integer,LinkedHashMap<FlowNetwork, Boolean>>> temporalIslandStatus;
    
    public MultiplexCascadeAgent(
            String experimentID,
            Time bootstrapTime, 
            Time runTime){
        super(experimentID, bootstrapTime, runTime);
        temporalIslandStatus = new HashMap();
    }

    /**
     * How to process event.
     * For now only handles (de)activation of nodes
     * 
     * @param event
     * @param sourceAddress 
     */
    @Override
    public void processIncomingEventMessage(Event event, NetworkAddress sourceAddress){
        logger.info("## Event Message");
        if(event.getEventType().equals(EventType.TOPOLOGY) && event.getNetworkComponent().equals(NetworkComponent.NODE) && event.getParameter().equals(NodeState.STATUS)){
            logger.info("## It's a node topology change");
            String sourceNodeID = event.getComponentID();
            Boolean sourceNodeStatus = (Boolean)event.getValue();
            logger.info("## Node " + sourceNodeID + " in net " + sourceAddress + " was set to activated = " + sourceNodeStatus);
            for(Node node : this.getInterdependentNetwork().getTargetNodes(sourceNodeID, sourceAddress, getPeer().getNetworkAddress())){
                logger.info("## targetNodeStatus = " + node.isActivated());
                logger.info("## sourceNodeStatus = " + sourceNodeStatus);
                if(node.isActivated() != sourceNodeStatus){
                    Event newEvent = new Event(this.getSimulationTime(), this.getIteration(), event.getEventType(), event.getNetworkComponent(), node.getIndex(), event.getParameter(), sourceNodeStatus);
                    this.queueEvent(newEvent);
                    setTopologyChanged(true);
                    logger.info("## setting topologyChanged = true");
                }
                else
                    logger.info("## No change here.");
            }
        }
    }
    
    @Override
    public void runInitialOperations(){        
        temporalIslandStatus.put(getSimulationTime(), new HashMap()); 
    }
    
    /**
     * Implements cascade as a result of overloaded links. 
     * Continues until system stabilizes, i.e. no more link overloads occur. 
     * Calls mitigateOverload method before finally calling linkOverload method, 
     * therefore mitigation strategies can be implemented by overwriting the method mitigateOverload.
     * Variable int iter = getIteration()-1
     */
    @Override
    public void runFlowAnalysis(){
        
        initRunFlowAnalysis();
        temporalIslandStatus.get(getSimulationTime()).put(getIteration(), new LinkedHashMap());
        
        // Go through all disconnected components (i.e. islands) of current iteration and perform flow analysis
        for(FlowNetwork island : this.getFlowNetwork().computeIslands()){
            logger.info("treating island with " + island.getNodes().size() + " nodes");
            boolean converged = flowConvergenceStrategy(island); 
            if(converged){
                mitigateOverload(island);
                boolean linkOverloaded = linkOverload(island);
                boolean nodeOverloaded = nodeOverload(island);
                if(linkOverloaded || nodeOverloaded){
                    setTopologyChanged(true);
                }
                else
                    temporalIslandStatus.get(getSimulationTime()).get(getIteration()).put(island, true);
            }
            else{
                updateNonConvergedIsland(island);
                setTopologyChanged(true);
                temporalIslandStatus.get(getSimulationTime()).get(getIteration()).put(island, false);
            }
        }

        logFinalIslands();
        // Output data at current iteration and go to next one
        nextIteration();

        // Tell the other networks that we're finished with this iteration
        this.sendStatusMessage(new StatusMessage(true, getIteration()));
    }
    
    
    /**
     * Domain specific strategy and/or necessary adjustments before backend is executed. 
     *
     * @param flowNetwork
     * @return true if flow analysis finally converged, else false
     */ 
    public boolean flowConvergenceStrategy(FlowNetwork flowNetwork){
        if (flowNetwork.getNodes().size() < 2)
            return false;
        boolean hasGen = false;
        for (Node node : flowNetwork.getNodes())
            if (node.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR))
                hasGen = true;
        if(hasGen)
            return this.getFlowDomainAgent().flowAnalysis(flowNetwork);
        else
            return false;
    }
    
    /**
     * Method to mitigate overload. Strategy to respond to (possible) overloading can be implemented here. This method is called before the OverLoadAlgo is called which deactivates affected links/nodes.
     * @param flowNetwork 
     */
    public void mitigateOverload(FlowNetwork flowNetwork){
        logger.debug("..no overload mitigation strategy implemented.");
    }
    
    /**
     * Checks link limits. If a limit is violated, an event 
     * for deactivating the link at the current simulation time is created.
     * @param flowNetwork
     * @return if overload happened
     */
    public boolean linkOverload(FlowNetwork flowNetwork){
        boolean overloaded = false;
        for (Link link : flowNetwork.getLinks()){
            if(link.isActivated() && link.getFlow() > link.getCapacity()){
                logger.info("..violating link " + link.getIndex() + " limit: " + link.getFlow() + " > " + link.getCapacity());
                updateOverloadLink(link);
                overloaded = true;
            }
        }
        return overloaded;
    }
    
    /**
     * Changes the parameters of the link after an overload happened.
     * @param link which is overloaded
     */
    public void updateOverloadLink(Link link){
        Event event = new Event(getSimulationTime(),EventType.TOPOLOGY,NetworkComponent.LINK,link.getIndex(),LinkState.STATUS,false);
        this.queueEvent(event);
    }
    
    /**
     * Checks node limits. If a limit is violated, an event 
     * for deactivating the node at the current simulation time is created.
     * @param flowNetwork
     * @return if overload happened
     */
    public boolean nodeOverload(FlowNetwork flowNetwork){
        boolean overloaded = false;
        for (Node node : flowNetwork.getNodes()){
            if(node.isActivated() && node.getFlow() > node.getCapacity()){
                logger.info("..violating node " + node.getIndex() + " limit: " + node.getFlow() + " > " + node.getCapacity());
                updateOverloadNode(node);
                // Uncomment if node overload should be included
                // overloaded = true;
            }
        }
        return overloaded;
    }
    
    /**
     * Changes the parameters of the node after an overload happened.
     * @param node which is overloaded
     */
    public void updateOverloadNode(Node node){
        logger.info("..doing nothing to overloaded node.");
    }
    
    /**
     * Adjust the network part which didn't converge.
     * @param flowNetwork 
     */
    public void updateNonConvergedIsland(FlowNetwork flowNetwork){
        for (Node node : flowNetwork.getNodes()){
            Event event = new Event(getSimulationTime(), getIteration()+1, EventType.TOPOLOGY, NetworkComponent.NODE, node.getIndex(), NodeState.STATUS, false);
            this.queueEvent(event);
        }
    }
    
    /**
     * Prints final islands in each time step to console
     */
    private void logFinalIslands(){
        String log = "----> " + temporalIslandStatus.get(getSimulationTime()).get(getIteration()).size() + " island(s) at iteration " + getIteration() + ":\n";
        String nodesInIsland;
        for (FlowNetwork net : temporalIslandStatus.get(getSimulationTime()).get(getIteration()).keySet()){
            nodesInIsland = "";
            for (Node node : net.getNodes())
                nodesInIsland += node.getIndex() + ", ";
            log += "    - " + net.getNodes().size() + " Node(s) (" + nodesInIsland + ")";
            if(temporalIslandStatus.get(getSimulationTime()).get(getIteration()).get(net))
                log += " -> Converged :)\n";
            if(!temporalIslandStatus.get(getSimulationTime()).get(getIteration()).get(net))
                log += " -> Blackout\n";
        }
        logger.info(log);
    }    
    
}