/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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
package org.sleuthkit.autopsy.ingest.events;

import java.io.Serializable;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.datamodel.Content;

/**
 * Event published when analysis (ingest) of a data source is started.
 */
public final class DataSourceAnalysisStartedEvent extends DataSourceAnalysisEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs an event published when analysis (ingest) of a data source is
     * started.
     *
     * @param ingestJobId The identifier of the ingest job. For a mulit-user
     *                    case, this ID is only unique on the node where the
     *                    ingest job is running.
     * @param dataSource  The data source.
     */
    public DataSourceAnalysisStartedEvent(long ingestJobId, Content dataSource) {
        super(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED, ingestJobId, dataSource);
    }

    /**
     * Constructs an event published when analysis (ingest) of a data source is
     * started.
     *
     * @param ingestJobId           The identifier of the ingest job. For a
     *                              mulit-user case, this ID is only unique on
     *                              the node where the ingest job is running.
     * @param dataSourceIngestJobId The identifier of the ingest job. For a
     *                              mulit-user case, this ID is only unique on
     *                              the node where the ingest job is running.
     * @param dataSource            The data source.
     *
     * @deprecated Do not use.
     */
    @Deprecated
    public DataSourceAnalysisStartedEvent(long ingestJobId, long dataSourceIngestJobId, Content dataSource) {
        super(IngestManager.IngestJobEvent.DATA_SOURCE_ANALYSIS_STARTED, ingestJobId, dataSource);
    }

}
