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

import input.FlowNetworkDataTypesInterface;
import input.FlowLoaderNew;
import org.apache.log4j.Logger;

/**
 *
 * @author Ben
 */
public class InterdependentFlowLoader extends FlowLoaderNew{
    private static final Logger logger = Logger.getLogger(InterdependentFlowLoader.class);
    
    /**
     *
     * @param interTopo
     * @param columnSeparator
     * @param missingValue
     * @param flowNetworkDataTypes
     */
    public InterdependentFlowLoader(InterdependentNetwork interTopo, String columnSeparator,  String missingValue, FlowNetworkDataTypesInterface flowNetworkDataTypes){
        super(interTopo, columnSeparator, missingValue, flowNetworkDataTypes);
    }
    
    @Override
    public void loadNodeFlowData(String location){
        logger.debug("Method loadNodeFlowData doesn't make sense for interdependent network.");
    }
}
