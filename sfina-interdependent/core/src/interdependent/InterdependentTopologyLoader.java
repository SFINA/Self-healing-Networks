/*
 * Copyright (C) 2016 SFINA Team
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
package interdependent;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Scanner;
import java.util.StringTokenizer;
import network.FlowNetwork;
import network.Link;
import network.LinkState;
import network.Node;
import org.apache.log4j.Logger;
import protopeer.network.NetworkAddress;

/**
 *
 * @author Ben
 */
public class InterdependentTopologyLoader{
    private static final Logger logger = Logger.getLogger(InterdependentTopologyLoader.class);
    private final InterdependentNetwork interNet;
    private final String columnSeparator;
    
    /**
     *
     * @param interNet
     * @param columnSeparator
     */
    public InterdependentTopologyLoader(InterdependentNetwork interNet, String columnSeparator){
        this.interNet = interNet;
        this.columnSeparator = columnSeparator;
    }
    
    public void loadNodes(String location){
        logger.debug("Method loadNodes doesn't make sense for this interdependent network implementation.");
    }
    
    public void loadLinks(String location){
        // Remove links to prevent links to be added multiple times (can happen if they don't have same indices in each time step)
        if (!interNet.getLinks().isEmpty()){
            ArrayList<Link> links = new ArrayList<>(interNet.getLinks());
            for (Link link : links)
                interNet.removeLink(link);
        }
        HashMap<NetworkAddress, FlowNetwork> nets = interNet.getInterdependentNetworks();
        ArrayList<LinkState> linkStates=new ArrayList<LinkState>();
        File file = new File(location);
        Scanner scr = null;
        try {
            scr = new Scanner(file);
            if(scr.hasNext()){
                StringTokenizer st = new StringTokenizer(scr.next(), columnSeparator);
                while(st.hasMoreTokens()){
                    // Same as for nodes, here properties like ID, from_node, to_node, etc are added but never used.
                    LinkState linkState=this.lookupLinkState(st.nextToken());
                    linkStates.add(linkState);
                }
            }
            while(scr.hasNext()){
                ArrayList<String> values=new ArrayList<String>();
                StringTokenizer st = new StringTokenizer(scr.next(), columnSeparator);
                while(st.hasMoreTokens()){
                    values.add(st.nextToken());
                }
                String linkIndex=(String)this.getActualLinkValue(linkStates.get(0), values.get(0));
                String startNodeIndex=(String)this.getActualLinkValue(linkStates.get(1), values.get(1));
                String startNetIndex=(String)this.getActualLinkValue(linkStates.get(2), values.get(2));
                String endNodeIndex=(String)this.getActualLinkValue(linkStates.get(3), values.get(3));
                String endNetIndex=(String)this.getActualLinkValue(linkStates.get(4), values.get(4));
                boolean status=(Boolean)this.getActualLinkValue(linkStates.get(5), values.get(5));
                Node startNode=null;
                NetworkAddress startNodeAddress=null;
                Node endNode=null;
                NetworkAddress endNodeAddress=null;
                for(NetworkAddress a : nets.keySet()){
                    for(Node node : nets.get(a).getNodes()){
                        if(startNodeIndex.equals(node.getIndex()) && a.toString().equals(startNetIndex)){
                            startNode=node;
                            startNodeAddress=a;
                        }
                        if(endNodeIndex.equals(node.getIndex()) && a.toString().equals(endNetIndex)){
                            endNode=node;
                            endNodeAddress=a;
                        }
                    }
                }
                if(startNode!=null && endNode!=null && startNodeAddress!=null && endNodeAddress!=null){
                    InterLink link=new InterLink(linkIndex,status,startNode,endNode,startNodeAddress,endNodeAddress);
                    interNet.addLink(link);
                }
                else{
                    logger.debug("Something went wrong with the indices of nodes and links.");
		}
            }
        }
        catch (FileNotFoundException ex) {
            ex.printStackTrace();
        }
    }
    
    public LinkState lookupLinkState(String linkState){
        switch(linkState){
            case "id":
                return LinkState.ID;
            case "from_node_id":
                return LinkState.FROM_NODE;
            case "to_node_id":
                return LinkState.TO_NODE;
            case "from_net_id":
                return LinkState.FROM_NET;
            case "to_net_id":
                return LinkState.TO_NET;
            case "status":
                return LinkState.STATUS;
            default:
                logger.debug("Link state is not recognized.");
                return null;
        }
    }
    
    public Object getActualLinkValue(LinkState linkState, String rawValue){
        switch(linkState){
            case ID:
                return rawValue;
            case FROM_NODE:
                return rawValue;
            case TO_NODE:
                return rawValue;
            case FROM_NET:
                return rawValue;
            case TO_NET:
                return rawValue;
            case STATUS:
                switch(rawValue){
                    case "1":
                        return true;
                    case "0":
                        return false;
                    default:
                        logger.debug("Something is wrong with status of the links.");
                }
            default:
                logger.debug("Link state is not recognized.");
                return null;
        }    
    }
    
    private void loadMultiplexNetwork(){
        int maxLinkId = 0;
        for(NetworkAddress address1 : interNet.getNetworkAddresses()){
            for(NetworkAddress address2 : interNet.getNetworkAddresses()){
                if(address1 != address2){
//                    FlowNetwork net1 = this.nets.get(address1);
//                    FlowNetwork net2 = this.nets.get(address2);
                    for (Node node1 : interNet.getInterdependentNetworks().get(address1).getNodes()){
                        for (Node node2 : interNet.getInterdependentNetworks().get(address2).getNodes()){
                            if(node1.getIndex().equals(node2.getIndex())){
                                maxLinkId += 1;
                                InterLink newLink = new InterLink(String.valueOf(maxLinkId), true, node1, node2, address1, address2);
                                interNet.addLink(newLink);
                            }
                        }
                    }
                }
            }
        }
    }
}
