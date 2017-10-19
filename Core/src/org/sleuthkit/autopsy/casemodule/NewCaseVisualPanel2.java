/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2017 Basis Technology Corp.
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

 /*
 * NewCaseVisualPanel2.java
 *
 * Created on Mar 7, 2012, 11:01:48 AM
 */
package org.sleuthkit.autopsy.casemodule;

import org.openide.util.NbBundle;

/**
 *
 * @author dfickling
 */
class NewCaseVisualPanel2 extends javax.swing.JPanel {

    OptionalCasePropertiesPanel propertiesPanel;
    /**
     * Creates new form NewCaseVisualPanel2
     */
    public NewCaseVisualPanel2() {
        initComponents();
        propertiesPanel = new OptionalCasePropertiesPanel();
        this.add(propertiesPanel);
        propertiesPanel.setVisible(true);
        
    }

    /**
     * Returns the name of the this panel. This name will be shown on the left
     * panel of the "New Case" wizard panel.
     *
     * @return name the name of this panel
     */
    @Override
    public String getName() {
        return NbBundle.getMessage(this.getClass(), "NewCaseVisualPanel2.getName.text");
    }



    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        setLayout(new java.awt.BorderLayout());
    }// </editor-fold>//GEN-END:initComponents
   
    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables

    String getCaseNumber() {
        return propertiesPanel.getCaseNumber();
    }

    String getExaminerName() {
        return propertiesPanel.getExaminerName();
    }
    
    String getExaminerPhone() {
        return propertiesPanel.getExaminerPhone();
    }
    
    String getExaminerEmail() {
        return propertiesPanel.getExaminerEmail();
    }
    
    String getExaminerNotes() {
        return propertiesPanel.getExaminerNotes();
    }
    
    String getOrganization() {
       return propertiesPanel.getOrganization();
    }

    void refreshCentralRepoFields() {
       propertiesPanel.setUpCentralRepoFields();
    }

}
