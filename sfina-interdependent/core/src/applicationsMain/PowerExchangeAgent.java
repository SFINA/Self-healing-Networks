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

import ModifiedJSwarmMain.Neighborhood;
import ModifiedJSwarmMain.Neighborhood1D;
import ModifiedJSwarmMain.Particle;
import ModifiedJSwarmMain.Swarm;
import ObjectiveFunctionMain.DefineObjectiveFunctionCharging;
import ObjectiveFunctionMain.MyParticle;
import backend.FlowBackendInterface;
import core.InterdependentAgentNoEvents;
import event.Event;
import event.EventType;
import event.NetworkComponent;
import interdependent.InterLink;
import interdependent.StatusMessage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import network.FlowNetwork;
import network.Link;
import network.LinkState;
import network.Node;
import network.NodeState;
import org.apache.log4j.Logger;
import power.backend.InterpssFlowBackend;
import power.backend.MATPOWERFlowBackend;
import power.backend.PowerBackendParameter;
import power.backend.PowerFlowType;
import power.input.PowerLinkState;
import power.input.PowerNodeState;
import power.input.PowerNodeType;
import protopeer.util.quantities.Time;

/**
 *
 * @author Ben
 */
public class PowerExchangeAgent extends InterdependentAgentNoEvents {

    public double maxCapacity;

    private static final Logger logger = Logger.getLogger(PowerExchangeAgent.class);

    private ArrayList<Node> generators;
    private Node slack;

    public ArrayList<Double> activatedLink = new ArrayList<Double>();
    public ArrayList<Double> servedLoad = new ArrayList<Double>();
    int local_iteration = 0;

    public ArrayList<Double> minimumPowerItCharging = new ArrayList<Double>();

    public ArrayList<Double> percentagePower = new ArrayList<Double>();

    // Keeps track of which islands exist in which time step and iteration
    private HashMap<Integer, HashMap<Integer, LinkedHashMap<FlowNetwork, Boolean>>> temporalIslandStatus;

    public ArrayList<Double> optimumFitness = new ArrayList<Double>();

    public ArrayList<Double> stateCharge = new ArrayList<Double>();

    public PowerExchangeAgent(
            String experimentID,
            Time bootstrapTime,
            Time runTime,
            double maxCapacity) {
        super(experimentID, bootstrapTime, runTime);
        temporalIslandStatus = new HashMap();

        this.maxCapacity = maxCapacity;
    }

    FlowBackendInterface flowBackend;
    HashMap<Enum, Object> ham = new HashMap<Enum, Object>();

    @Override
    public void runInitialOperations() {

        //loadInputData("time_1");
        this.setCapacityByToleranceParameter();
        temporalIslandStatus.put(getSimulationTime(), new HashMap());
    }

    @Override
    public void runFinalOperations() {
        try (
                PrintStream outPearson = new PrintStream(new File("linkSurvived_" + getPeer().getNetworkAddress() + getTimeToken() + "_" + maxCapacity + ".txt"));) {
            String sc = "";

            for (int m = 0; m < activatedLink.size(); m++) {

                sc += activatedLink.get(m) + " ";
            }

            outPearson.println(sc);

            outPearson.close();

        } catch (FileNotFoundException p) {

            p.printStackTrace();
        }

        try (
                PrintStream outPearson = new PrintStream(new File("servedLoad_" + getPeer().getNetworkAddress() + getTimeToken() + "_" + maxCapacity + ".txt"));) {
            String sc = "";

            for (int m = 0; m < servedLoad.size(); m++) {

                sc += servedLoad.get(m) + " ";
            }

            outPearson.println(sc);

            outPearson.close();

        } catch (FileNotFoundException p) {

            p.printStackTrace();
        }
        local_iteration = 0;

    }

    /**
     * Substracts the incoming real power flow from the power demand of the
     * node. Also possible: add to power generation (but not all nodes are
     * generators). If the flow is positive, power demand is reduced. If
     * negative, demand is increased.
     *
     * @param node
     * @param incomingFlow
     * @return
     */
    @Override
    public Event updateEndNodeWithFlow(Node node, Double incomingFlow) {
        logger.debug("Incoming link with non-zero flow at node " + node.getIndex() + " at peer " + this.getPeer().getNetworkAddress());
        Enum nodeState = PowerNodeState.POWER_DEMAND_REAL;
        logger.debug("incoming = " + incomingFlow);
        Double newValue = (Double) node.getProperty(nodeState) - incomingFlow;
        return new Event(this.getSimulationTime(), EventType.FLOW, NetworkComponent.NODE, node.getIndex(), nodeState, newValue);
    }

    @Override
    public Event updateEndNodeWhenFailure(Node node) {
        logger.debug("Incoming link which was deactivated or with deactivated start node at node " + node.getIndex());
        // Deactivates the node, if the connected InterLink or its start node is deactivated.
        return new Event(this.getSimulationTime(), EventType.TOPOLOGY, NetworkComponent.NODE, node.getIndex(), NodeState.STATUS, false);
    }

    /**
     * Adjust the network part which didn't converge. Sets a negative flow to
     * outgoing interLinks from non-converged island and reduces its own power
     * demand accordingly (i.e. effectively doing load shedding).
     *
     * @param net
     */
//    public void updateNonConvergedIsland(FlowNetwork net){
//        logger.debug("Updating non-converged island at peer " + this.getPeer().getNetworkAddress()); //why only peer 0 played, it is coz no non-converged island in peer 1
//        
//        // Ensures that a fixed quantity is requested from other net, distributed over all available links.
//        //peer 0 has outgoing interlinks and peer 1 has incoming interlinks
//        double neededFlow =  -5.0;
//        Enum nodeState = PowerNodeState.POWER_DEMAND_REAL;
//        ArrayList<InterLink> links = new ArrayList<>();
//        for(InterLink link : this.getInterdependentNetwork().getOutgoingInterLinks(this.getPeer().getNetworkAddress())){
//            if(net.getNodes().contains(link.getStartNode()) && ((Double)link.getStartNode().getProperty(nodeState) > 0.0)){
//                logger.debug("contains interlink");
//                links.add(link);
//            }
//        }
//        
//        
//        // Getting power from other network and reducing own power demand accordingly (like load shedding).
//        // Until power demand in this island reached 0. Then deactivating the nodes.
//        boolean successful = false;
//        for(InterLink link : links){
//            Node node = link.getStartNode();
//            Double oldValue = (Double)node.getProperty(nodeState);
//            Double newValue = Math.max(0.0, oldValue + neededFlow/links.size());
//            logger.debug("old = " + oldValue + ", new = " + newValue);
//            link.setFlow(newValue - oldValue);
//            logger.debug("Setting flow of interLink " + link.getIndex() + " to " + (newValue - oldValue));
//            queueEvent(new Event(this.getSimulationTime(), EventType.FLOW, NetworkComponent.NODE, node.getIndex(), nodeState, newValue));
//            setNetworkChanged(true);
//            successful = true;
//        }
//        if(!successful){
//            for (Node node : net.getNodes()){
//                Event event = new Event(getSimulationTime(), EventType.TOPOLOGY, NetworkComponent.NODE, node.getIndex(), NodeState.STATUS, false);
//                queueEvent(event);
//                setNetworkChanged(true);
//            }
//            temporalIslandStatus.get(getSimulationTime()).get(getIteration()).put(net, false);
//        }
//    }
    public void loopPower(FlowNetwork currentIsland, int local_iteration, FlowNetwork ccr, Boolean batteryPresent, Integer activeLink, double genMax, int batteryCounter) {

        //integrate PSO maybe here, where there is interlink present
        logger.debug("Updating non-converged island at peer " + this.getPeer().getNetworkAddress()); //why only peer 0 played, it is coz no non-converged island in peer 1

        // Ensures that a fixed quantity is requested from other net, distributed over all available links.
        //peer 0 has outgoing interlinks and peer 1 has incoming interlinks
        //double neededFlow =  0.0; //50 also initiates cascading failure, advances iteration also due to topology change, more than it>11
        Enum nodeState = PowerNodeState.POWER_DEMAND_REAL;
        Enum genState = PowerNodeState.POWER_GENERATION_REAL;
        ArrayList<InterLink> links = new ArrayList<>();
        for (InterLink link : this.getInterdependentNetwork().getOutgoingInterLinks(this.getPeer().getNetworkAddress())) {
            if (currentIsland.getNodes().contains(link.getStartNode()) && (local_iteration < 4)) { //original is 4
                logger.debug("contains interlink");
                links.add(link); //if PD=0, don't add interlinks, means don't execute setNetworkChanged, so iteration stops
            }
        }

        //Getting power from other network and reducing own power demand accordingly (like load shedding).
        //Until power demand in this island reached 0. Then deactivating the nodes.
        //boolean successful = false;
        for (InterLink link : links) {

            //this added later-begin  
            //this added later-end
            ArrayList<Double> original_load = new ArrayList<Double>();

            for (Node nod : ccr.getNodes()) {
                original_load.add((Double) nod.getProperty(PowerNodeState.POWER_DEMAND_REAL));
            }

            boolean converged = flowConvergenceStrategy(currentIsland);
            if (converged) {
                mitigateOverload(currentIsland);
                boolean linkOverloaded = linkOverloadBattery(currentIsland, ccr, genMax, batteryCounter, batteryPresent, Integer.parseInt(link.getStartNode().getIndex()));
                
                for (Node nnn : currentIsland.getNodes()) {
                        logger.info("current island node demand given by: " + nnn.getProperty(PowerNodeState.POWER_DEMAND_REAL));
                        if (nnn.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)) {
                            logger.info("current island node generation given by: " + nnn.getProperty(PowerNodeState.POWER_GENERATION_REAL));
                        }
                            if (nnn.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.SLACK_BUS)) {
                            logger.info("slack yes");
                            }

                        

                    }                
                
                for (Node nnn : ccr.getNodes()) {
                        logger.info("ccr node demand given by: " + nnn.getProperty(PowerNodeState.POWER_DEMAND_REAL));
                        if (nnn.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)) {
                            logger.info("ccr node generation given by: " + nnn.getProperty(PowerNodeState.POWER_GENERATION_REAL));

                        }
                        if (nnn.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.SLACK_BUS)) {
                            logger.info("slack yes");
                            }

                    }
                
                
                if (String.valueOf(this.getPeer().getNetworkAddress()).equals("0")) {
                    Node node_inter = getFlowNetwork().getNode(Integer.toString(2)); //34 for case39 //also 13 for case30 also 23 for case30
                    double power_gen = (Double) node_inter.getProperty(PowerNodeState.POWER_GENERATION_REAL);//before demand

                    if (100.0 <= power_gen || power_gen <= 2.0) {
                        node_inter.replacePropertyElement(PowerNodeState.POWER_GENERATION_REAL, (Double) 60.0);
                    }
                    logger.info("______________________________________________________________this is crazy " + node_inter.getProperty(PowerNodeState.POWER_GENERATION_REAL));
                }

                boolean converged_new = flowConvergenceStrategy(ccr); //does powerflow with the parameter set by pso
                //System.out.println("unsettling param corrected"+ (Double) ccr.getNode("2").getProperty(PowerNodeState.POWER_GENERATION_REAL));
                if (converged_new) {
                    for (Link link1 : ccr.getLinks()) {
                        if (link1.isActivated() == false) {
                            activeLink += 1;
                        }
                    }

                    boolean linkOverloadedBattery = linkOverload(ccr, ccr);
                    boolean nodeOverload = nodeOverload(ccr);

                    if (linkOverloadedBattery) {

                        setNetworkChanged(true);
                    } else {
                        temporalIslandStatus.get(getSimulationTime()).get(getIteration()).put(currentIsland, true);
                    }
                } else {

                    for (Node nnn : ccr.getNodes()) {
                        logger.info("ccr final node demand given by: " + nnn.getProperty(PowerNodeState.POWER_DEMAND_REAL));
                        if (nnn.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)) {
                            logger.info("ccr final node generation given by: " + nnn.getProperty(PowerNodeState.POWER_GENERATION_REAL));

                        }
                        if (nnn.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.SLACK_BUS)) {
                            logger.info("slack yes");
                            }

                    }
                    updateNonConvergedIsland(ccr);

                    logger.info("here is non-converging island");

                    temporalIslandStatus.get(getSimulationTime()).get(getIteration()).put(currentIsland, true);
                }
            }

            Node node = link.getStartNode();

            //Here power exchange begins   
            //stateCharge.add(0.0); //need to remove this
            logger.info("StateCharge is" + stateCharge.get(stateCharge.size() - 1));

            double neededFlow = 9 * stateCharge.get(stateCharge.size() - 1);//5.0; 

            double oldValue = 0.0;
            //if (node.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)){
            //    oldValue = (Double)node.getProperty(genState);
            //} else{
            oldValue = (Double) node.getProperty(nodeState);
            //}

            //Double newValue = Math.max(0.0, oldValue + neededFlow/links.size());
            Double newValue = oldValue + neededFlow;///links.size();
            logger.debug("old = " + oldValue + ", new = " + newValue);
            link.setFlow(newValue - oldValue);
            logger.debug("Setting flow of interLink " + link.getIndex() + " to " + (newValue - oldValue));
            if (newValue - oldValue != 0) {
                queueEvent(new Event(this.getSimulationTime(), EventType.FLOW, NetworkComponent.NODE, node.getIndex(), nodeState, newValue));
                setNetworkChanged(true);
            }
            //Here power exchange ends 

        }

    }

    public void loopPowerShedding(FlowNetwork ccr) {
        double neededFlow = 5.0; //50 also initiates cascading failure, advances iteration also due to topology change, more than it>11
        Enum nodeState = PowerNodeState.POWER_DEMAND_REAL;
        Enum genState = PowerNodeState.POWER_GENERATION_REAL;
        ArrayList<InterLink> links = new ArrayList<>();
        for (InterLink link : this.getInterdependentNetwork().getOutgoingInterLinks(this.getPeer().getNetworkAddress())) {
            if (ccr.getNodes().contains(link.getStartNode()) && (local_iteration < 4)) { //original is 17
                logger.debug("contains interlink");
                links.add(link); //if PD=0, don't add interlinks, means don't execute setNetworkChanged, so iteration stops
            }
        }

        //Getting power from other network and reducing own power demand accordingly (like load shedding).
        //Until power demand in this island reached 0. Then deactivating the nodes.
        //boolean successful = false;
        for (InterLink link : links) {
            Node node = link.getStartNode();

            double oldValue = 0.0;
            //if (node.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)){
            //    oldValue = (Double)node.getProperty(genState);
            //} else{
            oldValue = (Double) node.getProperty(nodeState);
            //}

            //Double newValue = Math.max(0.0, oldValue + neededFlow/links.size());
            Double newValue = oldValue + neededFlow;///links.size();
            logger.debug("old = " + oldValue + ", new = " + newValue);
            link.setFlow(newValue - oldValue);
            logger.debug("Setting flow of interLink " + link.getIndex() + " to " + (newValue - oldValue));
            if (newValue - oldValue != 0) {
                queueEvent(new Event(this.getSimulationTime(), EventType.FLOW, NetworkComponent.NODE, node.getIndex(), nodeState, newValue));
                setNetworkChanged(true);
            }
        }

        for (Node nod : ccr.getNodes()) {
            nod.replacePropertyElement(PowerNodeState.POWER_DEMAND_REAL, (Double) nod.getProperty(PowerNodeState.POWER_DEMAND_REAL) - 0.4 * (Double) nod.getProperty(PowerNodeState.POWER_DEMAND_REAL)); //do some % of position[9] from several bus
        }

    }

    /**
     * Implements cascade as a result of overloaded links. Continues until
     * system stabilizes, i.e. no more link overloads occur. Calls
     * mitigateOverload method before finally calling linkOverload method,
     * therefore mitigation strategies can be implemented by overwriting the
     * method mitigateOverload. Variable int iter = getIteration()-1
     */
    @Override
    public void runFlowAnalysis() {
        local_iteration += 1;

        logger.debug("print local_iteration " + local_iteration);

        initRunFlowAnalysis();
        temporalIslandStatus.get(getSimulationTime()).put(getIteration(), new LinkedHashMap());
        int activeLink = 0;
        int batteryCounter = 0;
        // Go through all disconnected components (i.e. islands) of current iteration and perform flow analysis
        for (FlowNetwork island : this.getFlowNetwork().computeIslands()) {
            logger.info("treating island with " + island.getNodes().size() + " nodes");

            boolean batteryPresent = false;
            FlowNetwork ccr = island;

            //loopPowerShedding(island);
            loopPower(island, local_iteration, ccr, batteryPresent, activeLink, maxCapacity, batteryCounter);//original maxCapacity = 15

            if (batteryPresent == false) {

                boolean converged = flowConvergenceStrategy(island);

                if (converged) {
                    mitigateOverload(island);

                    boolean linkOverloaded = linkOverload(island, island);
                    boolean nodeOverloaded = nodeOverload(island);
                    if (linkOverloaded || nodeOverloaded) {
                        setNetworkChanged(true);
                    } else {
                        temporalIslandStatus.get(getSimulationTime()).get(getIteration()).put(island, true);
                    }
                } else {
                    updateNonConvergedIsland(island);

                }
            }
        }

        ArrayList<Double> linkPerIteration = new ArrayList<Double>();

        for (Link lin : getFlowNetwork().getLinks()) {

            linkPerIteration.add((lin.isActivated()) ? 1.0 : 0.0);

        }

        ArrayList<Double> loadPerIteration = new ArrayList<Double>();

        for (Node nod : getFlowNetwork().getNodes()) {

            loadPerIteration.add((Double) nod.getProperty(PowerNodeState.POWER_DEMAND_REAL));

        }

        activatedLink.add(calculateAverage(linkPerIteration));
        servedLoad.add(calculateAverage(loadPerIteration) * getFlowNetwork().getNodes().size());
        stateCharge.add(0.0);

        // Output data at current iteration and go to next one
        nextIteration();

        this.sendStatusMessage(new StatusMessage(isNetworkChanged(), getIteration()));
    }

    /**
     * Domain specific strategy and/or necessary adjustments before backend is
     * executed.
     *
     * @param flowNetwork
     * @return true if flow analysis finally converged, else false
     */
    public boolean flowConvergenceStrategy(FlowNetwork flowNetwork) {
        boolean converged = false;

        int counter = 0;

        // blackout if isolated node
        if (flowNetwork.getNodes().size() == 1) {
            logger.info("....not enough nodes");
            return converged;
        }

        // or for example to get all generators and the slack bus if it exists
        generators = new ArrayList();
        slack = null;
        converged = setGenerationArray(flowNetwork);
        if (!converged) {
            return converged;
        }

        // To sort generators by max power output
        Collections.sort(generators, new Comparator<Node>() {
            public int compare(Node node1, Node node2) {
                return Double.compare((Double) node1.getProperty(PowerNodeState.POWER_MAX_REAL), (Double) node2.getProperty(PowerNodeState.POWER_MAX_REAL));
            }
        }.reversed());

        int limViolation = 1;
        while (limViolation != 0) {
            converged = this.getFlowDomainAgent().flowAnalysis(flowNetwork);
            //converged=false;
            logger.info("....converged " + converged);
            if (converged) {
                limViolation = GenerationBalancing(flowNetwork, slack);

                // Without the following line big cases (like polish) even DC doesn't converge..
                PowerFlowType flowType = (PowerFlowType) getDomainParameters().get(PowerBackendParameter.FLOW_TYPE);
                if (flowType.equals(PowerFlowType.DC)) {
                    limViolation = 0;
                }

                if (limViolation != 0) {
                    converged = false;

                    if (generators.size() > 0) { // make next bus a slack
                        slack.replacePropertyElement(PowerNodeState.TYPE, PowerNodeType.GENERATOR);
                        if (limViolation == 1) {
                            slack = generators.get(0);
                            generators.remove(0);
                        } else {
                            slack = generators.get(generators.size() - 1);
                            generators.remove(generators.size() - 1);
                        }
                        slack.replacePropertyElement(PowerNodeState.TYPE, PowerNodeType.SLACK_BUS);
                    } else {
                        counter++;
                        logger.info("....no more generators");

                        if (counter > 10) {
                            limViolation = 0;
                        }
                        //limViolation=0;
                        for (Node node : flowNetwork.getNodes()) {
                            node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REAL, (Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL) * 0.95);
                            //node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REACTIVE, (Double)node.getProperty(PowerNodeState.POWER_DEMAND_REACTIVE)*0.95);
                        }
                        //return false; // all generator limits were hit -> blackout
                        //converged = callBackend(flowNetwork);
                        converged = setGenerationArray(flowNetwork);
                        if (!converged) {
                            return converged;
                        }

                        // To sort generators by max power output
                        Collections.sort(generators, new Comparator<Node>() {
                            public int compare(Node node1, Node node2) {
                                return Double.compare((Double) node1.getProperty(PowerNodeState.POWER_MAX_REAL), (Double) node2.getProperty(PowerNodeState.POWER_MAX_REAL));
                            }
                        }.reversed());
                    }
                }
            } else {
                converged = loadShedding(flowNetwork);
                if (!converged) {
                    return false; // blackout if no convergence after load shedding
                }
            }
        }

        //break;
        return converged;
    }

    private boolean setGenerationArray(FlowNetwork flowNetwork) {
        boolean converged = true;
        for (Node node : flowNetwork.getNodes()) {
            if (node.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)) {
                generators.add(node);
            }
            if (node.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.SLACK_BUS)) {
                slack = node;
            }
        }

        // check if there's a slack in the island, if not make the generator with biggest power output to a slack bus
        if (slack == null) {
            if (generators.size() == 0) {
                double pd = 0.0;
                //add generators and slack bus (or just slack bus) (remove return converged)
                for (Node node : flowNetwork.getNodes()) {
                    pd += (Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL);
                    if (node.getIndex().equals("19") == true) {//before 19 (this is for case30)
                        //optimizeServiceability(flowNetwork);
                        for (Node node_new : flowNetwork.getNodes()) {
                            if (node_new.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.GENERATOR)) {
                                generators.add(node);
                            }
                            if (node_new.getProperty(PowerNodeState.TYPE).equals(PowerNodeType.SLACK_BUS)) {
                                slack = node;
                            }
                        }
                    }
                }
                logger.info("....no generator found");
                logger.info("size with no generators..." + flowNetwork.getNodes().size());

                //oneDischargingBattery(flowNetwork); //19 is the optimal position
                converged = false; // blackout if no generator in island (remove this if want to make island operational, set to false when you dont need battery)
            } else {
                slack = generators.get(0);
                // this is how one changes node/link properties
                slack.replacePropertyElement(PowerNodeState.TYPE, PowerNodeType.SLACK_BUS);
                generators.remove(0);
//                        generators.retainAll(generators1);
            }
        }
        return converged;
    }

    private int GenerationBalancing(FlowNetwork flowNetwork, Node slack) {
        int limViolation = 0;
        if ((Double) slack.getProperty(PowerNodeState.POWER_GENERATION_REAL) - (Double) slack.getProperty(PowerNodeState.POWER_MAX_REAL) > 0.001) {
            slack.replacePropertyElement(PowerNodeState.POWER_GENERATION_REAL, slack.getProperty(PowerNodeState.POWER_MAX_REAL));
            limViolation = 1;
        }
        if ((Double) slack.getProperty(PowerNodeState.POWER_GENERATION_REAL) - (Double) slack.getProperty(PowerNodeState.POWER_MIN_REAL) < -0.001) {
            slack.replacePropertyElement(PowerNodeState.POWER_GENERATION_REAL, slack.getProperty(PowerNodeState.POWER_MIN_REAL));
            limViolation = -1;
        }

        if (limViolation != 0) {
            logger.info("....generator limit violated at node " + slack.getIndex());
        } else {
            logger.info("....no generator limit violated");
        }
        return limViolation;
    }

    private boolean loadShedding(FlowNetwork flowNetwork) {
        boolean converged = false;
        int loadIter = 0;
        int maxLoadShedIterations = 15; // according to paper
        double loadReductionFactor = 0.05; // 5%, according to paper
        while (!converged && loadIter < maxLoadShedIterations) {
            logger.info("....Doing load shedding at iteration " + loadIter);
            for (Node node : flowNetwork.getNodes()) {
                node.replacePropertyElement(PowerNodeState.VOLTAGE_MAGNITUDE, (double) 1.0);
                node.replacePropertyElement(PowerNodeState.VOLTAGE_ANGLE, (double) 0.0);
                node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REAL, (Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL) * (1.0 - loadReductionFactor));
                node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REACTIVE, (Double) node.getProperty(PowerNodeState.POWER_DEMAND_REACTIVE) * (1.0 - loadReductionFactor));
            }
            converged = this.getFlowDomainAgent().flowAnalysis(flowNetwork);
            if (!converged) {
                for (Node node : flowNetwork.getNodes()) {
                    node.replacePropertyElement(PowerNodeState.VOLTAGE_MAGNITUDE, (double) 1.0);
                    node.replacePropertyElement(PowerNodeState.VOLTAGE_ANGLE, (double) 0.0);
                }
            }
            loadIter++;
        }
        return converged;
    }

    public void updateOverloadLink(Link link) {
        this.queueEvent(new Event(getSimulationTime(), EventType.TOPOLOGY, NetworkComponent.LINK, link.getIndex(), LinkState.STATUS, false));
        //Event event = new Event(getSimulationTime(), EventType.TOPOLOGY, NetworkComponent.LINK, link.getIndex(), LinkState.STATUS, false);
        //this.getEvents().add(event);
        // Power specific adjustments:
        this.queueEvent(new Event(getSimulationTime(), EventType.FLOW, NetworkComponent.LINK, link.getIndex(), PowerLinkState.CURRENT, 0.0));
        this.queueEvent(new Event(getSimulationTime(), EventType.FLOW, NetworkComponent.LINK, link.getIndex(), PowerLinkState.POWER_FLOW_FROM_REAL, 0.0));
        this.queueEvent(new Event(getSimulationTime(), EventType.FLOW, NetworkComponent.LINK, link.getIndex(), PowerLinkState.POWER_FLOW_FROM_REACTIVE, 0.0));
        this.queueEvent(new Event(getSimulationTime(), EventType.FLOW, NetworkComponent.LINK, link.getIndex(), PowerLinkState.POWER_FLOW_TO_REAL, 0.0));
        this.queueEvent(new Event(getSimulationTime(), EventType.FLOW, NetworkComponent.LINK, link.getIndex(), PowerLinkState.POWER_FLOW_TO_REACTIVE, 0.0));
    }

//    
    public void updateNonConvergedIsland(FlowNetwork flowNetwork) {
        logger.info("size of non-converged is " + flowNetwork.getNodes().size());
        for (Node node : flowNetwork.getNodes()) {
            node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REAL, 0.0);
            node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REACTIVE, 0.0);
            node.replacePropertyElement(PowerNodeState.POWER_GENERATION_REAL, 0.0);
            node.replacePropertyElement(PowerNodeState.POWER_GENERATION_REACTIVE, 0.0);
            node.replacePropertyElement(PowerNodeState.VOLTAGE_MAGNITUDE, 0.0);
            node.replacePropertyElement(PowerNodeState.VOLTAGE_ANGLE, 0.0);
        }
        for (Link link : flowNetwork.getLinks()) {
            link.replacePropertyElement(PowerLinkState.CURRENT, 0.0);
            link.replacePropertyElement(PowerLinkState.POWER_FLOW_FROM_REAL, 0.0);
            link.replacePropertyElement(PowerLinkState.POWER_FLOW_FROM_REACTIVE, 0.0);
            link.replacePropertyElement(PowerLinkState.POWER_FLOW_TO_REAL, 0.0);
            link.replacePropertyElement(PowerLinkState.POWER_FLOW_TO_REACTIVE, 0.0);
        }
    }

    /**
     * @return the toleranceParameter
     */
    public double getToleranceParameter() {
        return (Double) this.getDomainParameters().get(PowerBackendParameter.TOLERANCE_PARAMETER);
    }

    /**
     * @param toleranceParameter the toleranceParameter to set
     */
    public void setToleranceParameter(double toleranceParameter) {
        this.getDomainParameters().put(PowerBackendParameter.TOLERANCE_PARAMETER, toleranceParameter);
    }

    /**
     * Method to mitigate overload. Strategy to respond to (possible)
     * overloading can be implemented here. This method is called before the
     * OverLoadAlgo is called which deactivates affected links/nodes.
     *
     * @param flowNetwork
     */
    public void mitigateOverload(FlowNetwork flowNetwork) {

    }

    /**
     * Checks link limits. If a limit is violated, an event for deactivating the
     * link at the current simulation time is created.
     *
     * @param flowNetwork
     * @return if overload happened
     */
    public boolean linkOverload(FlowNetwork flowNetwork, FlowNetwork newflowNetwork) {
        int link_overload = 0;
        boolean overloaded = false;
        for (Link link : flowNetwork.getLinks()) {
            if (link.isActivated() && Math.abs(link.getFlow()) > Math.abs(link.getCapacity())) {
                logger.info("..violating link " + link.getIndex() + " limit: " + link.getFlow() + " > " + link.getCapacity());
                updateOverloadLink(link);
                overloaded = true;
                link_overload += 1;
            }
        }
        return overloaded;
    }

    public boolean linkOverloadBattery(FlowNetwork flowNetwork, FlowNetwork newflowNetwork, double genMax, int batteryCounter, Boolean batteryPresent, int pos) {
        int link_overload = 0;
        boolean overloaded = false;
        for (Link link : flowNetwork.getLinks()) {
            if (link.isActivated() && Math.abs(link.getFlow()) > Math.abs(link.getCapacity())) {
                logger.info("..violating link " + link.getIndex() + " limit: " + link.getFlow() + " > " + link.getCapacity());
                //updateOverloadLink(link);
                overloaded = true;
                link_overload += 1;

            }
        }
        System.out.println("link overload before " + link_overload);
        if (link_overload > Math.ceil(0.1 * flowNetwork.getLinks().size())) { //originally 0.1 when 0.9 reproduces result without battery
            batteryPresent = true;

            logger.info("cascade should be minimized");
            double fit_value = minimizeCascade(newflowNetwork, pos, genMax);
            System.out.print("fit value " + fit_value);
//            if (fit_value <= -300) {
//                batteryPresent = false;
//            }
            batteryCounter += 1;
            optimumFitness.add(minimizeCascade(newflowNetwork, pos, genMax));
            stateCharge.add(minimizeCascade(newflowNetwork, pos, genMax));
        }
        return overloaded;

    }

    /**
     * Changes the parameters of the link after an overload happened.
     *
     * @param link which is overloaded
     */
//    public void updateOverloadLink(Link link){
//        Event event = new Event(getSimulationTime(),EventType.TOPOLOGY,NetworkComponent.LINK,link.getIndex(),LinkState.STATUS,false);
//        this.queueEvent(event);
//    }
    /**
     * Checks node limits. If a limit is violated, an event for deactivating the
     * node at the current simulation time is created.
     *
     * @param flowNetwork
     * @return if overload happened
     */
    public boolean nodeOverload(FlowNetwork flowNetwork) {
        boolean overloaded = false;
        for (Node node : flowNetwork.getNodes()) {
            if (node.isActivated() && Math.abs(node.getFlow()) > Math.abs(node.getCapacity())) {
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
     *
     * @param node which is overloaded
     */
    public void updateOverloadNode(Node node) {
        logger.info("..doing nothing to overloaded node.");
    }

    /**
     * Prints final islands in each time step to console
     */
    private void logFinalIslands() {
        String log = "----> " + temporalIslandStatus.get(getSimulationTime()).get(getIteration()).size() + " island(s) at iteration " + getIteration() + ":\n";
        String nodesInIsland;
        for (FlowNetwork net : temporalIslandStatus.get(getSimulationTime()).get(getIteration()).keySet()) {
            nodesInIsland = "";
            for (Node node : net.getNodes()) {
                nodesInIsland += node.getIndex() + ", ";
            }
            log += "    - " + net.getNodes().size() + " Node(s) (" + nodesInIsland + ")";
            if (temporalIslandStatus.get(getSimulationTime()).get(getIteration()).get(net)) {
                log += " -> Converged :)\n";
            }
            if (!temporalIslandStatus.get(getSimulationTime()).get(getIteration()).get(net)) {
                log += " -> Blackout\n";
            }
        }
        logger.info(log);
    }

    private void setCapacityByToleranceParameter() {
        double toleranceParameter = 2.0;
        boolean capacityNotSet = false;
        for (Link link : getFlowNetwork().getLinks()) {
            if (link.getCapacity() == 0.0) {
                capacityNotSet = true;
            } else {
                capacityNotSet = true;
            }
        }
        if (capacityNotSet) {
            logger.debug("---------------------- Updating capacity");
            this.getFlowDomainAgent().flowAnalysis(getFlowNetwork());
            for (Link link : getFlowNetwork().getLinks()) {
                link.setCapacity(toleranceParameter * link.getFlow());
            }
        }
    }

    public double minimizeCascade(FlowNetwork flowNet, int pos, double genMax) {
        int numDimen = flowNet.getLinks().size();

        Double tot_load = 0.0;
        for (Node node : flowNet.getNodes()) {
            //link.setFlow(position[30+Integer.parseInt(link.getIndex())-1]);
            tot_load += Math.abs(Math.abs((Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL)));

        }

        ArrayList<Double> capacity = new ArrayList<Double>();
        int i = 0;
        for (Link link : flowNet.getLinks()) {

            capacity.add(Math.abs(link.getCapacity()));
            System.out.println("give capacity " + capacity.get(i));
            i += 1;
            //node.replacePropertyElement(PowerNodeState.TYPE, PowerNodeType.BUS);
        }

        System.out.println("Begin: runJswarm\n");

        //sets the dimension of the particle
        //NEW ADD BEGIN
        //Particle part = MyParticle.fixDimensions(new int[]{flowNet.getLinks().size() + 1});
        Particle part = MyParticle.fixDimensions(new int[]{numDimen + 2});
        //NEW ADD END

        //part.allocate(flowNet.getLinks().size() + 1);
        //part.setDimension(flowNet.getLinks().size() + 1);
        // Create a swarm (using 'MyParticle' as sample particle and 'MyObjectiveFunction' as fitness function)
        Swarm swarm = new Swarm(Swarm.DEFAULT_NUMBER_OF_PARTICLES, part, new DefineObjectiveFunctionCharging(flowNet, pos));

        // Use neighborhood
        Neighborhood neigh = new Neighborhood1D(Swarm.DEFAULT_NUMBER_OF_PARTICLES / 5, true);
        swarm.setNeighborhood(neigh);
        swarm.setNeighborhoodIncrement(0.9);

        int size = flowNet.getNodes().size();
        int link_size = flowNet.getLinks().size();

        //NEW ADD BEGIN
        //double[] maxim = new double[link_size + 1];//+link_size
        double[] maxim = new double[numDimen + 2];//+link_size
        //NEW ADD END

        //NEW ADD BEGIN
        //for (int j = 0; j < link_size; j++) {
        for (int j = 0; j < numDimen; j++) {
            maxim[j] = capacity.get(j);//+size
            //System.out.println("capacity each.."+ maxim[size+j]);
        }
        //NEW ADD END

        //NEW ADD BEGIN
        //double[] minim = new double[link_size + 1];//+link_size+1
        double[] minim = new double[numDimen + 2];//+link_size+1
        //NEW ADD END

        //NEW ADD BEGIN
        //for (int j = 0; j < link_size; j++) {
        for (int j = 0; j < numDimen; j++) {
            minim[j] = 0.0; //+size
        }
        //NEW ADD END

        //NEW ADD BEGIN
        //maxim[link_size] = 10;//tot_load / 10; //+link_size
        maxim[numDimen] = 10;//tot_load / 10; //+link_size
        //minim[link_size] = genMax; //arraycopy workds to concatenate two array +size //before 10 and 30
        minim[numDimen] = genMax; //arraycopy workds to concatenate two array +size //before 10 and 30
        maxim[numDimen + 1] = 1.0;
        minim[numDimen + 1] = 0.1;
        //NEW ADD END

        swarm.setInertia(0.95);
        swarm.setMaxPosition(maxim); //before 50 and 50
        swarm.setMinPosition(minim); //before 1 and 1
        swarm.setMaxMinVelocity(0.1); //before 0.1

        minimumPowerItCharging.add(0.0);
        minimumPowerItCharging.add(0.6);

        for (int p = 0; p < 4; p++) {
            //while (Math.abs(minimumPowerItCharging.get(minimumPowerItCharging.size() - 1) - minimumPowerItCharging.get(minimumPowerItCharging.size() - 2)) > 0.01) {
            swarm.evolve();
            minimumPowerItCharging.add(swarm.getBestFitness());
        }

        // Print results
        System.out.println(swarm.toStringStats());

        System.out.println("End: runJswarm");
        System.out.println("The End Fitness:" + swarm.getBestFitness());

        //swarm.getBestPosition();
        double[] best_pos = swarm.getBestPosition();

        return best_pos[best_pos.length - 1]; //correct
        //return swarm.getBestFitness();
    }

    public double calculateAverage(ArrayList<Double> arrayList) {
        double sum = 0.0;
        for (int i = 0; i < arrayList.size(); i++) {
            sum += arrayList.get(i);
        }

        return sum / arrayList.size();
    }

}
