/*
 * Autopsy Forensic Browser
 * 
 * Copyright 2021 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.datamodel;

import javax.swing.Action;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.directorytree.CollapseAction;
import org.sleuthkit.autopsy.mainui.nodes.DataArtifactTypeFactory;

/**
 * Analysis Results node support.
 */
@NbBundle.Messages({
    "DataArtifacts_name=Data Artifacts",
    "DataArtifacts_Collapse_All_Name=Collapse All"})
public class DataArtifacts implements AutopsyVisitableItem {

    private static final Logger logger = Logger.getLogger(DataArtifacts.class.getName());

    /**
     * Returns the name of this node that is the key in the children object.
     *
     * @return The name of this node that is the key in the children object.
     */
    public static String getName() {
        return Bundle.DataArtifacts_name();
    }

    /**
     * Parent node of all data artifacts.
     */
    public static class RootNode extends Artifacts.BaseArtifactNode {

        private static Children getChildren(long filteringDSObjId) {
            return Children.create(
                    new DataArtifactTypeFactory(filteringDSObjId > 0 ? filteringDSObjId : null), true);
        }
        private final long filteringDSObjId;

        /**
         * Main constructor.
         *
         * @param filteringDSObjId The data source object id for which results
         *                         should be filtered. If no filtering should
         *                         occur, this number should be less than or
         *                         equal to 0.
         */
        RootNode(long filteringDSObjId) {
            super(getChildren(filteringDSObjId),
                    "org/sleuthkit/autopsy/images/extracted_content.png",
                    DataArtifacts.getName(),
                    DataArtifacts.getName());
            this.filteringDSObjId = filteringDSObjId;
        }
        
        public Node clone() {
            return new RootNode(this.filteringDSObjId);
        }
        
        @Override
        public Action[] getActions(boolean context) {
            Action[] actions = new Action[1];
            actions[0] = new CollapseAction(Bundle.DataArtifacts_Collapse_All_Name());
            return actions;
        }
    }

    private final long datasourceObjId;

    /**
     * Main constructor.
     */
    public DataArtifacts() {
        this(0);
    }

    /**
     * Main constructor.
     *
     * @param dsObjId The data source object id.
     */
    public DataArtifacts(long dsObjId) {
        this.datasourceObjId = dsObjId;
    }

    @Override
    public <T> T accept(AutopsyItemVisitor<T> visitor) {
        return visitor.visit(this);
    }

    /**
     * Returns whether or not there is a data source object for which results
     * should be filtered.
     *
     * @return Whether or not there is a data source object for which results
     *         should be filtered.
     */
    Long getFilteringDataSourceObjId() {
        return datasourceObjId;
    }
}
