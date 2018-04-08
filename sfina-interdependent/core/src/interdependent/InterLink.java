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

import network.Link;
import network.Node;
import org.apache.log4j.Logger;
import protopeer.network.NetworkAddress;

/**
 * 
 * @author Ben
 */
public class InterLink extends Link{
    
    private static final Logger logger = Logger.getLogger(InterLink.class);
    
    private final NetworkAddress startNodeAddress;
    private final NetworkAddress endNodeAddress;

    /**
     * Instantiates an interdependent link 
     * which contains the addresses of adjacent nodes.
     * @param index
     * @param activated
     * @param startNode
     * @param endNode
     * @param startNodeAddress
     * @param endNodeAddress 
     */
    public InterLink(String index, boolean activated, Node startNode, Node endNode, NetworkAddress startNodeAddress, NetworkAddress endNodeAddress) {
        super(index, activated, startNode, endNode);
        this.startNodeAddress = startNodeAddress;
        this.endNodeAddress = endNodeAddress;
    }

    /**
     * @return the startNodeAddress
     */
    public NetworkAddress getStartNodeAddress() {
        return startNodeAddress;
    }

    /**
     * @return the endNodeAddress
     */
    public NetworkAddress getEndNodeAddress() {
        return endNodeAddress;
    }

}
