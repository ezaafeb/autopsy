/** *************************************************************************
 ** This data and information is proprietary to, and a valuable trade secret
 ** of, Basis Technology Corp. It is given in confidence by Basis Technology
 ** and may only be used as permitted under the license agreement under which
 ** it has been distributed, and in no other way.
 **
 ** Copyright (c) 2023 Basis Technology Corp. All rights reserved.
 **
 ** The technical data and information provided herein are provided with
 ** `limited rights', and the computer software provided herein is provided
 ** with `restricted rights' as those terms are defined in DAR and ASPR
 ** 7-104.9(a).
 ************************************************************************** */
package com.basistech.df.cybertriage.autopsy.ctoptions;

import com.basistech.df.cybertriage.autopsy.ctoptions.subpanel.CTOptionsSubPanel;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.swing.JPanel;
import org.netbeans.spi.options.OptionsPanelController;
import org.openide.util.Lookup;
import org.sleuthkit.autopsy.ingest.IngestModuleGlobalSettingsPanel;

/**
 * Options panel for CyberTriage.
 */
public class CTOptionsPanel extends IngestModuleGlobalSettingsPanel {

    private static final int MAX_SUBPANEL_WIDTH = 500;

    private static final Logger logger = Logger.getLogger(CTOptionsPanel.class.getName());

    private final List<CTOptionsSubPanel> subPanels;

    /**
     * Creates new form CTOptions
     */
    public CTOptionsPanel() {
        initComponents();
        Collection<? extends CTOptionsSubPanel> coll = Lookup.getDefault().lookupAll(CTOptionsSubPanel.class);
        Stream<? extends CTOptionsSubPanel> panelStream = coll != null ? coll.stream() : Stream.empty();
        this.subPanels = panelStream
                .map(panel -> {
                    try {
                        // lookup is returning singleton instances which means this panel gets messed up when accessed
                        // from multiple places because the panel's children are being added to a different CTOptionsPanel
                        return (CTOptionsSubPanel) panel.getClass().getConstructor().newInstance();
                    } catch (Exception ex) {
                        return null;
                    }
                })
                .filter(item -> item != null)
                .sorted(Comparator.comparing(p -> p.getClass().getSimpleName().toUpperCase()))
                .collect(Collectors.toList());
        addSubOptionsPanels(this.subPanels);
    }

    private void addSubOptionsPanels(List<CTOptionsSubPanel> subPanels) {
        for (int i = 0; i < subPanels.size(); i++) {
            CTOptionsSubPanel subPanel = subPanels.get(i);

            subPanel.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if (evt.getPropertyName().equals(OptionsPanelController.PROP_CHANGED)) {
                        CTOptionsPanel.this.firePropertyChange(OptionsPanelController.PROP_CHANGED, null, null);
                    }
                }
            });

            GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = i;
            gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints.insets = new java.awt.Insets(i == 0 ? 5 : 0, 5, 5, 5);
            gridBagConstraints.weighty = 0;
            gridBagConstraints.weightx = 0;

            contentPane.add(subPanel, gridBagConstraints);
        }

        GridBagConstraints verticalConstraints = new GridBagConstraints();
        verticalConstraints.gridx = 0;
        verticalConstraints.gridy = subPanels.size();
        verticalConstraints.weighty = 1;
        verticalConstraints.weightx = 0;

        JPanel verticalSpacer = new JPanel();

        verticalSpacer.setMinimumSize(new Dimension(MAX_SUBPANEL_WIDTH, 0));
        verticalSpacer.setPreferredSize(new Dimension(MAX_SUBPANEL_WIDTH, 0));
        verticalSpacer.setMaximumSize(new Dimension(MAX_SUBPANEL_WIDTH, Short.MAX_VALUE));
        contentPane.add(verticalSpacer, verticalConstraints);

        GridBagConstraints horizontalConstraints = new GridBagConstraints();
        horizontalConstraints.gridx = 1;
        horizontalConstraints.gridy = 0;
        horizontalConstraints.weighty = 0;
        horizontalConstraints.weightx = 1;

        JPanel horizontalSpacer = new JPanel();
        contentPane.add(horizontalSpacer, horizontalConstraints);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.JScrollPane scrollPane = new javax.swing.JScrollPane();
        contentPane = new javax.swing.JPanel();

        setLayout(new java.awt.BorderLayout());

        contentPane.setLayout(new java.awt.GridBagLayout());
        scrollPane.setViewportView(contentPane);

        add(scrollPane, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    @Override
    public void saveSettings() {
        subPanels.forEach(panel -> panel.saveSettings());
    }

    public void loadSavedSettings() {
        subPanels.forEach(panel -> panel.loadSettings());
    }

    public boolean valid() {
        return subPanels.stream().allMatch(panel -> panel.valid());
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel contentPane;
    // End of variables declaration//GEN-END:variables
}
