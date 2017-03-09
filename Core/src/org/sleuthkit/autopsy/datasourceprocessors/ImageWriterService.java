/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2016 Basis Technology Corp.
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
package org.sleuthkit.autopsy.datasourceprocessors;

import java.util.HashSet;
import java.util.Set;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.NbBundle;
import org.openide.util.lookup.ServiceProvider;
import org.openide.util.lookup.ServiceProviders;
import org.sleuthkit.autopsy.framework.AutopsyService;

@ServiceProviders(value = {@ServiceProvider(service = AutopsyService.class)})

public class ImageWriterService implements AutopsyService {

    private static final Set<ImageWriter> imageWriters = new HashSet<>();  // Contains all Image Writer objects
    private static final Object imageWritersLock = new Object(); // Get this lock before accessing currentImageWriters    
    
    /**
     * Create an image writer object for the given data source ID.
     * @param imageId ID for the image
     */
    public static void createImageWriter(Long imageId){
        
        // ImageWriter objects are created during the addImageTask. They can not arrive while
        // we're closing case resources so we don't need to worry about one showing up while
        // doing our close/cleanup.
        synchronized(imageWritersLock){
            ImageWriter writer = new ImageWriter(imageId);
            writer.subscribeToEvents();
            imageWriters.add(writer);
        }
    }
    
    @Override
    public String getServiceName() {
        return NbBundle.getMessage(this.getClass(), "ImageWriterService.serviceName");
    }
    
    @Override
    public void closeCaseResources(CaseContext context) throws AutopsyServiceException {
        context.getProgressIndicator().progress("Waiting for VHD(s) to complete");

        synchronized(imageWritersLock){
            // If any of our ImageWriter objects haven't started the finish task, set the cancel flag
            // to make sure they don't start now. The reason they haven't started is that
            // ingest was not complete, and the user already confirmed that they want to exit
            // even though ingest is not complete so we will take that to mean that they
            // also don't want to wait for Image Writer.
            for(ImageWriter writer: imageWriters){
                writer.cancelIfNotStarted();
            }
            
            // Test whether any finishImage tasks are in progress
            boolean jobsAreInProgress = false;
            for(ImageWriter writer: imageWriters){
                if(writer.jobIsInProgress()){
                    jobsAreInProgress = true;
                    break;
                }
            }
            
            if(jobsAreInProgress){
                // If jobs are in progress, ask the user if they want to wait for them to complete
                NotifyDescriptor descriptor = new NotifyDescriptor.Confirmation(
                    "Wait for Image Writer to finish?",
                    "Title",
                    NotifyDescriptor.YES_NO_OPTION,
                    NotifyDescriptor.WARNING_MESSAGE);
                descriptor.setValue(NotifyDescriptor.NO_OPTION);
                Object response = DialogDisplayer.getDefault().notify(descriptor);
                
                if(response == DialogDescriptor.NO_OPTION){
                    // Cancel all the jobs
                    for(ImageWriter writer: imageWriters){
                        writer.cancelJob();
                    }
                }
                
                // Wait for all finishImage jobs to complete. If the jobs got cancelled
                // this will be very fast.
                for(ImageWriter writer: imageWriters){
                    writer.waitForJobToFinish();
                }
                
            }
        }
    }
}
