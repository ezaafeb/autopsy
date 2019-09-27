/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
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

package org.sleuthkit.autopsy.communications.relationships;

import java.util.TimeZone;
import java.util.logging.Level;
import org.sleuthkit.autopsy.communications.Utils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import static org.sleuthkit.datamodel.BlackboardAttribute.TSK_BLACKBOARD_ATTRIBUTE_VALUE_TYPE.DATETIME;
import org.sleuthkit.datamodel.TimeUtilities;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A set of reusable utility functions for the Relationships package.
 * 
 */
final class RelationshipsNodeUtilities {
    
    private static final Logger logger = Logger.getLogger(RelationshipsNodeUtilities.class.getName());
    
    // Here to make codacy happy
    private RelationshipsNodeUtilities(){
    }
    
     /**
     *
     * Get the display string for the attribute of the given type from the given
     * artifact.
     *
     * @param artifact      the value of artifact
     * @param attributeType the value of TSK_SUBJECT1
     *
     * @return The display string, or an empty string if there is no such
     *         attribute or an an error.
     */
    static String getAttributeDisplayString(final BlackboardArtifact artifact, final BlackboardAttribute.ATTRIBUTE_TYPE attributeType) {
        try {
            BlackboardAttribute attribute = artifact.getAttribute(new BlackboardAttribute.Type(BlackboardAttribute.ATTRIBUTE_TYPE.fromID(attributeType.getTypeID())));
            if (attribute == null) {
                return "";
            } else if (attributeType.getValueType() == DATETIME) {
                return TimeUtilities.epochToTime(attribute.getValueLong(),
                        TimeZone.getTimeZone(Utils.getUserPreferredZoneId()));
            } else {
                return attribute.getDisplayString();
            }
        } catch (TskCoreException tskCoreException) {
            logger.log(Level.WARNING, "Error getting attribute value.", tskCoreException); //NON-NLS
            return "";
        }
    }
    
    	/**
	 * Normalize the phone number by removing all non numeric characters, except
	 * for leading +.
	 *
	 * @param phoneNum The phone number to normalize
	 *
	 * @return The normalized phone number.
	 */
    static String normalizePhoneNum(String phoneNum) {
        String normailzedPhoneNum = phoneNum.replaceAll("\\D", "");

        if (phoneNum.startsWith("+")) {
            normailzedPhoneNum = "+" + normailzedPhoneNum;
        }

        if (normailzedPhoneNum.isEmpty()) {
            normailzedPhoneNum = phoneNum;
        }

        return normailzedPhoneNum;
    }

    /**
     * Normalize the given email address by converting it to lowercase.
     *
     * @param emailAddress The email address tot normalize
     *
     * @return The normalized email address.
     */
    static String normalizeEmailAddress(String emailAddress) {
        String normailzedEmailAddr = emailAddress.toLowerCase();

        return normailzedEmailAddr;
    }
}
