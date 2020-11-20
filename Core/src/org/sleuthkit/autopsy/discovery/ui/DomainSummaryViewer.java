/*
 * Autopsy
 *
 * Copyright 2020 Basis Technology Corp.
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
package org.sleuthkit.autopsy.discovery.ui;

import javax.swing.DefaultListModel;
import javax.swing.event.ListSelectionListener;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.discovery.search.DiscoveryEventUtils;

/**
 * A JPanel to display domain summaries.
 */
public class DomainSummaryViewer extends javax.swing.JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultListModel<DomainWrapper> domainListModel = new DefaultListModel<>();

    /**
     * Creates new form DomainSummaryPanel
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    public DomainSummaryViewer() {
        initComponents();
    }

    /**
     * Clear the list of documents being displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void clearViewer() {
        domainListModel.removeAllElements();
        domainScrollPane.getVerticalScrollBar().setValue(0);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        domainScrollPane = new javax.swing.JScrollPane();
        domainList = new javax.swing.JList<>();

        setLayout(new java.awt.BorderLayout());

        domainList.setModel(domainListModel);
        domainList.setCellRenderer(new DomainSummaryPanel());
        domainScrollPane.setViewportView(domainList);

        add(domainScrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JList<DomainWrapper> domainList;
    private javax.swing.JScrollPane domainScrollPane;
    // End of variables declaration//GEN-END:variables

    /**
     * Add the summary for a domain to the panel.
     *
     * @param domainWrapper The object which contains the domain summary which
     *                      will be displayed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void addDomain(DomainWrapper domainWrapper) {
        domainListModel.addElement(domainWrapper);
    }

    /**
     * Send an event to perform the population of the domain details tabs to
     * reflect the currently selected domain. Will populate the list with
     * nothing when a domain is not used.
     *
     * @param useDomain If the currently selected domain should be used to
     *                  retrieve a list.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void sendPopulateEvent(boolean useDomain) {
        String domain = "";
        if (useDomain) {
            if (domainList.getSelectedIndex() != -1) {
                domain = domainListModel.getElementAt(domainList.getSelectedIndex()).getResultDomain().getDomain();
            }
        }
        //send populateMesage
        DiscoveryEventUtils.getDiscoveryEventBus().post(new DiscoveryEventUtils.PopulateDomainTabsEvent(domain));
    }

    /**
     * Add a selection listener to the list of document previews being
     * displayed.
     *
     * @param listener The ListSelectionListener to add to the selection model.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    void addListSelectionListener(ListSelectionListener listener) {
        domainList.getSelectionModel().addListSelectionListener(listener);
    }
}