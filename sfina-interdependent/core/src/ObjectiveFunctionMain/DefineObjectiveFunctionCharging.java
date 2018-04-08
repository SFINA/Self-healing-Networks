/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ObjectiveFunctionMain;

import backend.FlowBackendInterface;
import input.Domain;
import input.SfinaParameter;
import java.util.HashMap;
import network.FlowNetwork;
import network.Node;
import power.backend.PowerFlowType;
import power.backend.InterpssFlowBackend;
import power.input.PowerLinkState;
import power.input.PowerNodeState;
import power.backend.PowerBackendParameter;

import ModifiedJSwarmMain.FitnessFunction;
import java.util.ArrayList;
import network.Link;
import power.input.PowerNodeType;

/**
 *
 * @author Manish
 */
public class DefineObjectiveFunctionCharging extends FitnessFunction {

    private HashMap<Enum, Object> backendParameters;
    private HashMap<SfinaParameter, Object> sfinaParameters;
    private Domain domain;
    private FlowNetwork network;
    private int flowgate_position;

    /**
     * Default constructor
     */
    public DefineObjectiveFunctionCharging(FlowNetwork net, int flowgate_pos) {
        super(net, flowgate_pos);

        this.network = net;
        this.flowgate_position = flowgate_pos;

    }

    public double evaluate(double position[], FlowNetwork net, int flowgate_pos) {
        int numDimen=net.getLinks().size();
        FlowBackendInterface flowBackend;

        int ii = 0;

        flowgate_position = flowgate_pos;
        network = net;
        double result = 0;
        boolean converged = false;
        double sum1 = 0;
        double final_load = 0;
        HashMap<Enum, Object> ham = new HashMap<Enum, Object>();

        Node node_demand = network.getNode(Integer.toString(flowgate_position)); //34 for case39 //also 13 for case30 also 23 for case30
        node_demand.replacePropertyElement(PowerNodeState.POWER_GENERATION_REAL, (Double) node_demand.getProperty(PowerNodeState.POWER_GENERATION_REAL) - position[numDimen]);//network.getLinks().size()]);//163-position[9]);
        for (Node node : network.getNodes()) {
        if (ii < 2) {
            node.replacePropertyElement(PowerNodeState.POWER_DEMAND_REAL, position[numDimen+1]*(Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL)); //- position[numDimen+1]*(Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL)); //do some % of position[9] from several bus
         }            ii += 1;
        }

        ham.put(PowerBackendParameter.FLOW_TYPE, PowerFlowType.DC);
        flowBackend = new InterpssFlowBackend(ham);
        converged = flowBackend.flowAnalysis(network);

        for (Link link : network.getLinks()) { //put ceiling on network proportion for links
            //if (ii < numDimen) {
                link.replacePropertyElement(PowerLinkState.POWER_FLOW_FROM_REAL, position[ii]);//+30
            //}
            //ii += 1;
        }

        for (Link link : network.getLinks()) {
            sum1 += Math.abs(Math.abs((Double) link.getProperty(PowerLinkState.POWER_FLOW_FROM_REAL)));

        }

        for (Node node : network.getNodes()) {
            final_load += Math.abs(Math.abs((Double) node.getProperty(PowerNodeState.POWER_DEMAND_REAL)));

        }

        double power_gen = (Double) node_demand.getProperty(PowerNodeState.POWER_GENERATION_REAL);//before demand

        return Math.abs(power_gen);// - 0.5 * (power_gen - final_load));Math.abs(power_gen)+
        //return 2.0;
    }

    public void makeGenerator(Node node, Double power) { //here power should be optimized
        node.replacePropertyElement(PowerNodeState.TYPE, PowerNodeType.GENERATOR);
        node.replacePropertyElement(PowerNodeState.POWER_GENERATION_REAL, power);
        node.replacePropertyElement(PowerNodeState.POWER_GENERATION_REACTIVE, 0.0);
        node.replacePropertyElement(PowerNodeState.QC1_MAX, 0);
        node.replacePropertyElement(PowerNodeState.QC1_MIN, 0);
        node.replacePropertyElement(PowerNodeState.QC2_MAX, 0);
        node.replacePropertyElement(PowerNodeState.QC2_MIN, 0);
        node.replacePropertyElement(PowerNodeState.PC1, power);
        node.replacePropertyElement(PowerNodeState.PC2, power);
        node.replacePropertyElement(PowerNodeState.POWER_MAX_REAL, 600);
        node.replacePropertyElement(PowerNodeState.POWER_MIN_REAL, -600);
        node.replacePropertyElement(PowerNodeState.POWER_MAX_REACTIVE, 500);
        node.replacePropertyElement(PowerNodeState.POWER_MIN_REACTIVE, -500);
        node.replacePropertyElement(PowerNodeState.VOLTAGE_SETPOINT, 1);
        node.replacePropertyElement(PowerNodeState.BASE_VOLTAGE, 345.0); //line branch should have equal base volt 135 for case30, 345 for case39
        node.replacePropertyElement(PowerNodeState.RAMP_AGC, 0);
        node.replacePropertyElement(PowerNodeState.RAMP_10, 0);
        node.replacePropertyElement(PowerNodeState.RAMP_30, 0);
        node.replacePropertyElement(PowerNodeState.RAMP_REACTIVE_POWER, 0);
        node.replacePropertyElement(PowerNodeState.AREA_PART_FACTOR, 0);
        node.replacePropertyElement(PowerNodeState.MODEL, 2);
        node.replacePropertyElement(PowerNodeState.STARTUP, 0);
        node.replacePropertyElement(PowerNodeState.SHUTDOWN, 0);
        node.replacePropertyElement(PowerNodeState.COST_PARAM_1, 0.025);
        node.replacePropertyElement(PowerNodeState.COST_PARAM_2, 3);
        node.replacePropertyElement(PowerNodeState.COST_PARAM_3, 0);
        node.replacePropertyElement(PowerNodeState.N_COST, 3); //total 27 including GEN

    }

}
