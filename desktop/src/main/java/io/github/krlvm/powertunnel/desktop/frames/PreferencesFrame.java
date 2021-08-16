/*
 * This file is part of PowerTunnel.
 *
 * PowerTunnel is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PowerTunnel is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PowerTunnel.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.krlvm.powertunnel.desktop.frames;

import io.github.krlvm.powertunnel.desktop.BuildConstants;
import io.github.krlvm.powertunnel.desktop.ui.ComboBoxScroll;
import io.github.krlvm.powertunnel.desktop.ui.FieldFilter;
import io.github.krlvm.powertunnel.desktop.ui.SelectPreferenceRenderer;
import io.github.krlvm.powertunnel.desktop.ui.TextRightClickPopup;
import io.github.krlvm.powertunnel.desktop.utilities.UIUtility;
import io.github.krlvm.powertunnel.preferences.Preference;
import io.github.krlvm.powertunnel.preferences.PreferenceGroup;
import io.github.krlvm.powertunnel.preferences.PreferenceType;
import io.github.krlvm.powertunnel.sdk.configuration.Configuration;
import ru.krlvm.swingdpi.SwingDPI;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.text.PlainDocument;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class PreferencesFrame extends AppFrame {

    protected static Map<String, PreferencesFrame> OPENED_IDS = new HashMap<>();

    private final File configurationFile;
    private final Configuration configuration;
    private final List<PreferenceGroup> preferences;

    public PreferencesFrame(
            String title,
            String id,
            File configurationFile,
            Configuration configuration,
            List<PreferenceGroup> preferences
    ) {
        super(title);

        this.configurationFile = configurationFile;
        this.configuration = configuration;
        this.preferences = preferences;

        final JRootPane root = getRootPane();
        root.setLayout(new GridBagLayout());
        root.setBorder(BORDER);

        final ActionListener actionlistener = e -> updateDependencies();
        final ItemListener itemListener = e -> updateDependencies();

        final int len = preferences.size();
        for(int i = 0; i < len; i++) {
            final PreferenceGroup group = preferences.get(i);
            final List<JComponent> list = new ArrayList<>();

            for (Preference preference : group.getPreferences()) {
                final JPanel panel = new JPanel(new GridBagLayout());
                final GridBagConstraints c = new GridBagConstraints();

                c.gridx = 0;
                if(preference.getType() != PreferenceType.CHECKBOX && preference.getType() != PreferenceType.SWITCH) {
                    c.ipadx = SwingDPI.scale(5);
                    final JLabel label = new JLabel(preference.getTitle() + ":");
                    if(preference.getDescription() != null) UIUtility.setTooltip(label, preference.getDescription());
                    panel.add(label, c);
                    c.gridx++;
                }

                c.weightx = 1;
                c.fill = GridBagConstraints.HORIZONTAL;
                final JComponent value;
                switch (preference.getType()) {
                    case SWITCH:
                    case CHECKBOX: {
                        value = new JCheckBox(preference.getTitle(), getBooleanOption(preference));
                        ((JCheckBox) value).addItemListener(itemListener);
                        if(preference.getDescription() != null) UIUtility.setTooltip(value, preference.getDescription());
                        break;
                    }
                    case STRING:
                    case NUMBER: {
                        final JTextField field = new JTextField(preference.getType() == PreferenceType.STRING ?
                                getStringOption(preference) :
                                String.valueOf(getIntOption(preference))
                        );
                        if (preference.getType() == PreferenceType.NUMBER) {
                            ((PlainDocument) field.getDocument()).setDocumentFilter(new FieldFilter.Number());
                            field.addKeyListener(new KeyAdapter() {
                                @Override
                                public void keyPressed(KeyEvent e) {
                                    if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                                        field.setText(String.valueOf(Integer.parseInt(field.getText()) - 1));
                                    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                                        field.setText(String.valueOf(Integer.parseInt(field.getText()) + 1));
                                    }
                                }
                            });
                        }
                        TextRightClickPopup.register(field);
                        value = field;
                        break;
                    }
                    case SELECT: {
                        final List<Preference.SelectPreferenceItem> models = preference.getItemsAsModels();
                        final JComboBox<Preference.SelectPreferenceItem> comboBox = new JComboBox<>(models.toArray(new Preference.SelectPreferenceItem[0]));
                        comboBox.setSelectedItem(models.stream().filter(m -> m.getKey().equals(getStringOption(preference)))
                                .findFirst().orElse(models.get(0))
                        );
                        comboBox.setLightWeightPopupEnabled(false);
                        comboBox.setRenderer(new SelectPreferenceRenderer());
                        comboBox.addItemListener(itemListener);
                        ComboBoxScroll.register(comboBox);
                        value = comboBox;
                        break;
                    }
                    default:
                        throw new IllegalStateException("Unsupported type: " + preference.getType());
                }
                preference.binding = value;
                panel.add(value, c);

                list.add(panel);
            }

            insertComponent(createBlock(group.getTitle(), list));
            if(len > 1 && i != len-1) {
                insertComponent(Box.createVerticalStrut(SwingDPI.scale(4)));
            }
        }

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                final int result = JOptionPane.showConfirmDialog(PreferencesFrame.this,
                        "Do you want to save changes?", BuildConstants.NAME, JOptionPane.YES_NO_CANCEL_OPTION
                );
                if (result == JOptionPane.YES_OPTION) {
                    save();
                } else if(result == JOptionPane.NO_OPTION) {
                    dispose();
                }
            }
            @Override
            public void windowClosed(WindowEvent e) {
                OPENED_IDS.remove(id);
            }
        });
        OPENED_IDS.put(id, this);

        requestSpacing();
        frameInitialized();
    }

    public void insertComponent(Component component) {
        getRootPane().add(component, gbc);
    }

    @Override
    protected void frameInitialized() {
        final JPanel actionPanel = new JPanel(new BorderLayout());

        final JButton resetButton = new JButton("Reset");
        resetButton.addActionListener(e -> {
            final int result = JOptionPane.showConfirmDialog(PreferencesFrame.this,
                    "Do you want to reset configuration?", BuildConstants.NAME, JOptionPane.YES_NO_OPTION
            );
            if (result == JOptionPane.YES_OPTION) {
                configuration.clear();
                saveConfiguration();
                dispose();
            }
        });

        final JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        final JButton saveButton = new JButton("Save");
        saveButton.addActionListener(e -> {
            save();
            dispose();
        });

        final JPanel westWrapper = new JPanel(new FlowLayout());
        westWrapper.add(resetButton);

        final JPanel eastWrapper = new JPanel(new FlowLayout());
        eastWrapper.add(cancelButton);
        eastWrapper.add(saveButton);

        actionPanel.add(westWrapper, BorderLayout.WEST);
        actionPanel.add(eastWrapper, BorderLayout.EAST);
        insertComponent(actionPanel);

        saveButton.requestFocus();
        saveButton.requestFocusInWindow();
        getRootPane().setDefaultButton(saveButton);

        updateDependencies();
        pack();

        final Dimension screenResolution = Toolkit.getDefaultToolkit().getScreenSize();
        final double screenWidth = screenResolution.getWidth();
        final double screenHeight = screenResolution.getHeight();
        setSize(
                (int)(Math.min(getWidth() + SwingDPI.scale(75), screenWidth)),
                (int)(Math.min(getHeight(), screenHeight - (screenHeight * 0.1)))
        );

        super.frameInitialized();
    }

    protected void requestSpacing() {
        final GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        getRootPane().add(Box.createHorizontalGlue(), c);
    }

    private void onMalformedPreferences() {
        UIUtility.showErrorDialog(this, "Malformed preferences schema");
        dispose();
    }

    private String getStringOption(Preference preference) {
        return configuration.get(preference.getKey(), preference.getDefaultValue());
    }
    private int getIntOption(Preference preference) {
        return configuration.getInt(preference.getKey(), Integer.parseInt(preference.getDefaultValue()));
    }
    private boolean getBooleanOption(Preference preference) {
        return configuration.getBoolean(preference.getKey(), Boolean.parseBoolean(preference.getDefaultValue()));
    }

    private String getBindingValue(Preference preference) {
        final JComponent component = ((JComponent) preference.binding);
        switch (preference.getType()) {
            case SWITCH:
            case CHECKBOX: {
                return String.valueOf(((JCheckBox) component).isSelected());
            }
            case NUMBER:
            case STRING: {
                return ((JTextField) component).getText();
            }
            case SELECT: {
                final JComboBox<Preference.SelectPreferenceItem> cb = ((JComboBox<Preference.SelectPreferenceItem>) component);
                if(cb.getSelectedItem() == null) {
                    if(preference.getDefaultValue().equals("true")) return preference.getItems().keySet().iterator().next();
                    return preference.getItems().get(preference.getDefaultValue());
                }
                return ((Preference.SelectPreferenceItem) cb.getSelectedItem()).getKey();
            }
            default:
                throw new IllegalStateException("Unsupported type: " + preference.getType());
        }
    }
    private void save() {
        for (PreferenceGroup group : preferences) {
            for (Preference preference : group.getPreferences()) {
                configuration.set(preference.getKey(), getBindingValue(preference));
            }
        }
        saveConfiguration();
    }
    private void saveConfiguration() {
        try {
            configuration.save(configurationFile);
        } catch (IOException ex) {
            UIUtility.showErrorDialog(this, "Failed to save preferences: " + ex.getMessage());
            System.err.println("Failed to save preferences: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    private void updateDependencies() {
        for (PreferenceGroup group : preferences) {
            for (Preference preference : group.getPreferences()) {
                ((JComponent) preference.binding).setEnabled(isSatisfied(preference));
            }
        }
    }
    private boolean isSatisfied(Preference preference) {
        if(preference.binding == null) return true;
        final String dependency = preference.getDependency();
        if(dependency == null) return true;
        final Preference target = PreferenceGroup.findPreference(preferences, dependency);
        if(target == null) return true;
        if(target.binding == null) return true;

        return getBindingValue(target).equals(preference.getDependencyValue()) && isSatisfied(target);
    }


    private static JPanel createBlock(String title, List<JComponent> components) {
        gbc.ipady = SwingDPI.scale(3);
        final JPanel panel = new JPanel(new GridBagLayout());

        if(title != null) {
            final TitledBorder border = BorderFactory.createTitledBorder(title);
            border.setTitleJustification(TitledBorder.LEADING);
            panel.setBorder(BorderFactory.createCompoundBorder(border, GROUP_BORDER));
        }

        for (JComponent component : components) {
            if(component == null) continue;
            panel.add(component, gbc);
        }

        return panel;
    }
    private static final Border GROUP_BORDER = BorderFactory.createEmptyBorder(2, 4, 2, 4);
    private static final GridBagConstraints gbc = new GridBagConstraints();
    static {
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
    }
}