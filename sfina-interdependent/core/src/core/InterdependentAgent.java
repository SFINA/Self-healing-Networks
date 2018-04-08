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
package core;

import event.Event;
import event.EventType;
import event.NetworkComponent;
import input.SfinaParameter;
import interdependent.EventMessage;
import interdependent.InterdependentNetwork;
import interdependent.StatusMessage;
import java.io.File;
import java.util.ArrayList;
import java.util.Objects;
import network.FlowNetwork;
import network.Link;
import network.LinkState;
import network.Node;
import network.NodeState;
import org.apache.log4j.Logger;
import protopeer.network.Message;
import protopeer.network.NetworkAddress;
import protopeer.util.quantities.Time;

/**
 *
 * @author Ben
 */
public abstract class InterdependentAgent extends SimulationAgentNew{
    
    private static final Logger logger = Logger.getLogger(InterdependentAgent.class);
    
    private InterdependentNetwork interNet;
    private String interInputFilesLocation;
    private String interInputName;
    private String interTopologyInputName;
    private String interFlowInputName;
    private boolean topologyChanged;
    private ArrayList<StatusMessage> statusMessages;
    
    
    public InterdependentAgent(
            String experimentID,
            Time bootstrapTime, 
            Time runTime){
        super(experimentID, bootstrapTime, runTime);
        this.interInputFilesLocation = "experiments/"; 
        this.interInputName = "/interdependence/";
        this.interTopologyInputName = "/topology.txt";
        this.interFlowInputName = "/flow.txt";
    }
    
    public void addInterdependentNetwork(InterdependentNetwork interNet){
        this.interNet=interNet;
    }
    
    /**
     * @return the interTopology
     */
    public InterdependentNetwork getInterdependentNetwork() {
        return interNet;
    }
    
    /**
     * @return the topologyChanged
     */
    public boolean isTopologyChanged() {
        return topologyChanged;
    }

    /**
     * @param topologyChanged the topologyChanged to set
     */
    public void setTopologyChanged(boolean topologyChanged) {
        this.topologyChanged = topologyChanged;
    }
    
    @Override
    public void handleIncomingMessage(Message message) {
        logger.info("\n##### Incoming message at Peer " + this.getPeer().getIndexNumber() + " from Peer " + message.getSourceAddress());
        if(message instanceof EventMessage){
            EventMessage msg = (EventMessage)message;
            Event event = msg.getEvent();
            NetworkAddress sourceAddress = msg.getSourceAddress();
            processIncomingEventMessage(event, sourceAddress);
        }
        if(message instanceof StatusMessage){
            processIncomingStatusMessage((StatusMessage)message);
        }
    }
    
    /**
     * How to process event.
     * 
     * @param event
     * @param sourceAddress 
     */
    public abstract void processIncomingEventMessage(Event event, NetworkAddress sourceAddress);
    
    
    public void processIncomingStatusMessage(StatusMessage msg) {
        logger.info("## Status Message number " + (statusMessages.size()+1));
        statusMessages.add(msg);
        int numberOtherNets = this.getInterdependentNetwork().getNumberOfNets() - 1;
        // Only react when status messages from all other networks arrived
        if(statusMessages.size() == numberOtherNets){
            // Set the iteration of this network to the iteration of the other net(s).
            // This is necessary if there was no change in this network during the last iterations.
            // Before, check if all the other networks are at the same iterations.
            ArrayList<Integer> iterations = new ArrayList<>();
            for(StatusMessage statusMsg : statusMessages)
                iterations.add(statusMsg.getIteration());
            for(int i=0; i<iterations.size()-1; i++)
                if(!Objects.equals(iterations.get(i), iterations.get(i+1)))
                    logger.debug("Iterations of interdependent networks are not the same, which shouldn't happen.");
            if(this.getIteration() != msg.getIteration())
                this.setIteration(msg.getIteration());
            
            this.updateInterdependentLinks();
            
            if(isTopologyChanged()){
                logger.info("## Topology was changed -> Executing events and calling runFlowAnalysis\n");
                // Executing all events (both triggered by other networks and within this one)
                this.executeAllEvents();
                // Go to next iteration
                this.runFlowAnalysis();
            }
            else{
                statusMessages = new ArrayList<>();
                logger.info("## Topology was not changed -> Doing nothing.");
            }
        }
    }
    
    /**
     * Broadcast all executed events to all other peers.
     * @param event 
     */
    public void sendEventMessage(Event event){
        EventMessage message = new EventMessage(event);
        if(event.getEventType().equals(EventType.TOPOLOGY) && event.getNetworkComponent().equals(NetworkComponent.NODE) && event.getParameter().equals(NodeState.STATUS)){
            logger.info("##### Sending topology change message at Peer " + this.getPeer().getIndexNumber());
            for (NetworkAddress address : getInterdependentNetwork().getNetworkAddresses())
                if (!address.equals(getPeer().getNetworkAddress()))
                    getPeer().sendMessage(address, message);
        }
    }
    
    /**
     * Broadcast all executed events to all other peers. 
     * @param message
     */
    public void sendStatusMessage(StatusMessage message){
        logger.info("##### Sending status message at Peer " + this.getPeer().getIndexNumber());
        for (NetworkAddress address : getInterdependentNetwork().getNetworkAddresses())
            if (!address.equals(getPeer().getNetworkAddress()))
                getPeer().sendMessage(address, message);
    }
    
    /**
     *
     */
    public void updateInterdependentLinks(){
        
    };
    
    /**
     * Initializes the active state for interdependent networks.
     * - setting iteration = 1 and loading data like for non-interdependent
     * - loading interdependent links
     */
    @Override
    public void initActiveState(){
        this.setTimeToken(this.getTimeTokenName() + this.getSimulationTime());
        logger.info("\n\n--------------> " + this.getTimeToken() + " at peer" + getPeer().getIndexNumber() + " <--------------");
        resetIteration();        
        loadInputData(this.getTimeToken());
        loadInterdependentInputData(this.getTimeToken());
    }
    
    public void initRunFlowAnalysis(){
        logger.info("\n----> Iteration " + (getIteration()) + " at peer " + getPeer().getNetworkAddress() + " <----");
        setTopologyChanged(false);
        statusMessages = new ArrayList<>();
    }
    
    /**
     * Loads interdependent network data from input files at given time if folder is provided.
     * @param timeToken 
     */
//    public void loadInterdependentInputData(String timeToken){
//        File file = new File(this.getExperimentInputFilesLocation()+timeToken);
//        if (file.exists() && file.isDirectory()) {
//            logger.info("loading interdependent data at " + timeToken);
//            String interdependentTopologyLocation = this.interInputFilesLocation + this.getExperimentID() + this.interInputName + timeToken + this.interTopologyInputName;
//            String interdependentFlowLocation = this.interInputFilesLocation + this.getExperimentID() + this.interInputName + timeToken + this.interFlowInputName;
//            this.getInterdependentNetwork().updateTopology(this.getFlowNetwork(), this.getPeer().getNetworkAddress(), interdependentTopologyLocation, interdependentFlowLocation, this.getColumnSeparator(), this.getMissingValue(), this.getFlowDomainAgent().getFlowNetworkDataTypes());
//            logger.debug(this.getInterdependentNetwork().toString());
//        }
//    }
    
    public void loadInterdependentInputData(String timeToken){
        File file = new File(this.getExperimentBaseFolderLocation() + this.interInputName + timeToken);
        if (file.exists() && file.isDirectory()) {
            logger.info("loading interdependent data at " + timeToken);
            String interdependentTopologyLocation = this.interInputFilesLocation + this.getExperimentID() + this.interInputName + timeToken + this.interTopologyInputName;
            String interdependentFlowLocation = this.interInputFilesLocation + this.getExperimentID() + this.interInputName + timeToken + this.interFlowInputName;
            this.getInterdependentNetwork().updateNetwork(this.getFlowNetwork(), this.getPeer().getNetworkAddress(), interdependentTopologyLocation, interdependentFlowLocation, this.getColumnSeparator(), this.getMissingValue(), this.getFlowDomainAgent().getFlowNetworkDataTypes());
            this.getFlowDomainAgent().setFlowParameters(interNet);
            logger.debug(this.getInterdependentNetwork().toString());
        }
    }
    
    /**
     * Exexutes the event.
     * Addition to executeEvent in SimulationAgent:
     * - calls sendEventMessage
     * - also reloads Interdependent Network in case of reload event
     * @param flowNetwork
     * @param event
     */
    @Override
    public void executeEvent(FlowNetwork flowNetwork, Event event){
        if(event.getTime() == getSimulationTime()){
            
            this.getEventWriter().writeEvent(event);
            this.sendEventMessage(event);
            
            switch(event.getEventType()){
                case TOPOLOGY:
                    switch(event.getNetworkComponent()){
                        case NODE:
                            Node node=flowNetwork.getNode(event.getComponentID());
                            switch((NodeState)event.getParameter()){
                                case ID:
                                    node.setIndex((String)event.getValue());
                                    break;
                                case STATUS:
                                    if(node.isActivated() == (Boolean)event.getValue())
                                        logger.debug("Node status same, not changed by event.");
                                    node.setActivated((Boolean)event.getValue()); 
                                    logger.info("..setting node " + node.getIndex() + " to activated = " + event.getValue());
                                    break;
                                default:
                                    logger.debug("Node state cannot be recognised");
                            }
                            break;
                        case LINK:
                            Link link=flowNetwork.getLink(event.getComponentID());
                            link.replacePropertyElement(event.getParameter(), event.getValue());
                            switch((LinkState)event.getParameter()){
                                case ID:
                                    link.setIndex((String)event.getValue());
                                    break;
                                case FROM_NODE:
                                    link.setStartNode(flowNetwork.getNode((String)event.getValue()));
                                    break;
                                case TO_NODE:
                                    link.setEndNode(flowNetwork.getNode((String)event.getValue()));
                                    break;
                                case STATUS:
                                    if(link.isActivated() == (Boolean)event.getValue())
                                        logger.debug("Link status same, not changed by event.");
                                    link.setActivated((Boolean)event.getValue()); 
                                    logger.info("..setting link " + link.getIndex() + " to activated = " + event.getValue());
                                    break;
                                default:
                                    logger.debug("Link state cannot be recognised");
                            }
                            break;
                        default:
                            logger.debug("Network component cannot be recognised");
                    }
                    break;
                case FLOW:
                    switch(event.getNetworkComponent()){
                        case NODE:
                            Node node=flowNetwork.getNode(event.getComponentID());
                            node.replacePropertyElement(event.getParameter(), event.getValue());
                            break;
                        case LINK:
                            Link link=flowNetwork.getLink(event.getComponentID());
                            link.replacePropertyElement(event.getParameter(), event.getValue());
                            break;
                        default:
                            logger.debug("Network component cannot be recognised");
                    }
                    break;
                case SYSTEM:
                    logger.info("..executing system parameter event: " + (SfinaParameter)event.getParameter());
                    switch((SfinaParameter)event.getParameter()){
                        case RELOAD:
                            loadInputData("time_" + (String)event.getValue());
                            loadInterdependentInputData(this.getTimeTokenName() + (String)event.getValue());
                            break;
                        default:
                            logger.debug("System parameter cannot be recognized.");
                    }
                    break;
                default:
                    logger.debug("Event type cannot be recognised");
            }
        }
        else
            logger.debug("Event not executed because defined for different time step.");
    }
    
}