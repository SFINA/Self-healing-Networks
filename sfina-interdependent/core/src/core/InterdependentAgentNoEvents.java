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
import input.SfinaParameter;
import interdependent.InterdependentNetwork;
import interdependent.StatusMessage;
import java.io.File;
import java.util.ArrayList;
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
public abstract class InterdependentAgentNoEvents extends SimulationAgentNew{
    
    private static final Logger logger = Logger.getLogger(InterdependentAgentNoEvents.class);
    
    private InterdependentNetwork interNet;
    private String interInputFilesLocation;
    private String interInputName;
    private String interTopologyInputName;
    private String interFlowInputName;
    private boolean isNetworkChanged;
    private ArrayList<StatusMessage> statusMessages;
    
    
    public InterdependentAgentNoEvents(
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
     * @return the isInterNetChanged
     */
    public boolean isNetworkChanged() {
        return isNetworkChanged;
    }

    /**
     * @param isNetworkChanged the isInterNetChanged to set
     */
    public void setNetworkChanged(boolean isNetworkChanged) {
        logger.debug("Setting networkChanged = " + isNetworkChanged);
        this.isNetworkChanged = isNetworkChanged;
    }
    
    @Override
    public void handleIncomingMessage(Message message) {
        logger.info("\n##### Incoming message at Peer " + this.getPeer().getIndexNumber() + " from Peer " + message.getSourceAddress());
//        if(message instanceof EventMessage){
//            EventMessage msg = (EventMessage)message;
//            Event event = msg.getEvent();
//            NetworkAddress sourceAddress = msg.getSourceAddress();
//            //processIncomingEventMessage(event, sourceAddress);
//        }
        if(message instanceof StatusMessage){
            processIncomingStatusMessage((StatusMessage)message);
        }
    }
    
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
            Boolean netsChanged = false;
            for(StatusMessage statusMsg : statusMessages){
                iterations.add(statusMsg.getIteration());
                if(statusMsg.isNetworkChanged())
                    netsChanged = true;
        }
            iterations.add(getIteration());
            for(int i=0; i<iterations.size()-1; i++)
                if(iterations.get(i) - iterations.get(i+1) > 1){
                    logger.debug("Iterations differ more than 1 at networks: " + statusMessages.get(i) + ", " + statusMessages.get(i+1));
                    this.setIteration(msg.getIteration());
                }
            
            this.updateInterdependentLinks();
            
            if(isNetworkChanged()){
                logger.info("## Topology was changed -> Executing events and calling runFlowAnalysis at peer " + getPeer().getNetworkAddress());
                // Executing all events (both triggered by other networks and within this one)
                this.executeAllEvents();
                // Go to next iteration
                this.runFlowAnalysis();
                //this.sendStatusMessage(new StatusMessage(isNetworkChanged(),getIteration()));
            }
            else{
                statusMessages = new ArrayList<>();
                this.setIteration(this.getIteration()+1);
                logger.info("## Topology was not changed -> Doing nothing.");
                if (netsChanged)
                    this.sendStatusMessage(new StatusMessage(false,getIteration()));
                //logger.info("## Topology was not changed -> Doing nothing.");
                
                
            }
            
        }
        
    }
    
    /**
     * Broadcast all executed events to all other peers. 
     * @param message
     */
    public void sendStatusMessage(StatusMessage message){
        for (NetworkAddress address : getInterdependentNetwork().getNetworkAddresses())
            if (!address.equals(getPeer().getNetworkAddress())){
                logger.info("##### Sending status message from peer " + this.getPeer().getIndexNumber() + " to " + address);
                getPeer().sendMessage(address, message);
            }
    }
    
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
        logger.info("\n----> Iteration " + getIteration() + " at peer " + getPeer().getNetworkAddress() + " <----");
        statusMessages = new ArrayList<>();
        setNetworkChanged(false);
    }
    
    /**
     * Loads interdependent network data from input files at given time if folder is provided.
     * @param timeToken 
     */
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
     *  
     */
    public void updateInterdependentLinks(){
        for(Link link : this.getInterdependentNetwork().getIncomingInterLinks(this.getPeer().getNetworkAddress())){
            if(link.isConnected() && link.isActivated()){
                if(link.getFlow() != 0.0){
                    this.queueEvent(updateEndNodeWithFlow(link.getEndNode(), link.getFlow()));
                    this.setNetworkChanged(true);
                    link.setFlow(0.0);
                    logger.debug("Queued evend. Resetting flow of InterLink " + link.getIndex() + " to 0.0");
                }
            }
            else{
                this.queueEvent(updateEndNodeWhenFailure(link.getEndNode()));
                this.setNetworkChanged(true);
            }
        }
    };
    
    /**
     * Update the end node of an incoming InterLink flow quantity. 
     * This is executed if the flow in the InterLink is non-zero, 
     * and the link is connected and activated.
     * Backend specific.
     * 
     * @param node
     * @param incomingFlow
     * @return event encoding the corresponding change to the end node.
     */
    public abstract Event updateEndNodeWithFlow(Node node, Double incomingFlow);
    
    /**
     * Update the end node of an incoming failed InterLink. 
     * This is executed the incoming link is either disconnected or deactivated.
     * Backend independent
     * 
     * @param node
     * @return event encoding the corresponding change to the end node.
     */
    public abstract Event updateEndNodeWhenFailure(Node node);
    
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
            //this.sendEventMessage(event);
            
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
                    logger.info("..executing flow change event at " + event.getNetworkComponent() + " " + event.getComponentID() + ": " + event.getParameter() + " -> " + event.getValue());
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