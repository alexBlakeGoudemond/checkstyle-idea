package org.infernus.idea.checkstyle;

import org.infernus.idea.checkstyle.config.ApplicationConfigurationState;
import org.infernus.idea.checkstyle.ui.GlobalLocationTableModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckStyleApplicationConfigurableTest {

    private ApplicationConfigurationState applicationConfigurationState;
    private CheckStyleApplicationConfigurable configurable;

    @BeforeEach
    void setUp() {
        applicationConfigurationState = new ApplicationConfigurationState();
        configurable = new CheckStyleApplicationConfigurable(applicationConfigurationState);
        configurable.createComponent();
    }

    @Test
    void isNotModifiedInitially() {
        assertFalse(configurable.isModified());
    }

    @Test
    void isModifiedAfterFieldChanges() {
        configurable.getArtifactRepositoryBaseUrlOverrideField().setText("https://mirror.example.com/repo/");

        assertTrue(configurable.isModified());
    }

    @Test
    void applyPersistsFieldValueToState() {
        configurable.getArtifactRepositoryBaseUrlOverrideField().setText("https://mirror.example.com/repo/");

        configurable.apply();

        assertEquals("https://mirror.example.com/repo/", applicationConfigurationState.getArtifactRepositoryBaseUrlOverride());
        assertFalse(configurable.isModified());
    }

    @Test
    void applyWithBlankFieldClearsOverride() {
        applicationConfigurationState.setArtifactRepositoryBaseUrlOverride("https://mirror.example.com/repo/");
        configurable.reset();

        configurable.getArtifactRepositoryBaseUrlOverrideField().setText("   ");
        configurable.apply();

        assertNull(applicationConfigurationState.getArtifactRepositoryBaseUrlOverride());
    }

    @Test
    void resetLoadsFieldFromState() {
        applicationConfigurationState.setArtifactRepositoryBaseUrlOverride("https://mirror.example.com/repo/");

        configurable.reset();

        JTextField field = configurable.getArtifactRepositoryBaseUrlOverrideField();
        assertEquals("https://mirror.example.com/repo/", field.getText());
    }

    @Test
    void isModifiedWhenGlobalDefaultCheckboxChanges() {
        JCheckBox useGlobalByDefault = getPrivateField("useGlobalRulesByDefaultCheckbox", JCheckBox.class);

        useGlobalByDefault.setSelected(true);

        assertTrue(configurable.isModified());
    }

    @Test
    void applyPersistsGlobalLocationSettings() {
        GlobalLocationTableModel tableModel = getPrivateField("globalLocationTableModel", GlobalLocationTableModel.class);
        JCheckBox useGlobalByDefault = getPrivateField("useGlobalRulesByDefaultCheckbox", JCheckBox.class);

        ApplicationConfigurationState.GlobalConfigurationLocation location =
                new ApplicationConfigurationState.GlobalConfigurationLocation(
                        "glob-1", "LOCAL_FILE", "c:/rules.xml", "Global", "All");

        tableModel.setLocations(List.of(location), List.of("glob-1"));
        useGlobalByDefault.setSelected(true);

        configurable.apply();

        assertTrue(applicationConfigurationState.isUseGlobalRulesByDefault());
        assertEquals(List.of(location), applicationConfigurationState.getGlobalLocations());
        assertEquals(List.of("glob-1"), applicationConfigurationState.getActiveGlobalLocationIds());
        assertFalse(configurable.isModified());
    }

    @Test
    void activeIdOrderingDoesNotMarkSettingsModified() {
        ApplicationConfigurationState.GlobalConfigurationLocation one =
                new ApplicationConfigurationState.GlobalConfigurationLocation("id-1", "LOCAL_FILE", "c:/1.xml", "One", "All");
        ApplicationConfigurationState.GlobalConfigurationLocation two =
                new ApplicationConfigurationState.GlobalConfigurationLocation("id-2", "LOCAL_FILE", "c:/2.xml", "Two", "All");
        applicationConfigurationState.setGlobalLocations(List.of(one, two));
        applicationConfigurationState.setActiveGlobalLocationIds(List.of("id-1", "id-2"));

        configurable.reset();
        GlobalLocationTableModel tableModel = getPrivateField("globalLocationTableModel", GlobalLocationTableModel.class);
        tableModel.setValueAt(false, 0, 0);
        tableModel.setValueAt(true, 0, 0);

        assertFalse(configurable.isModified());
    }

    private <T> T getPrivateField(String fieldName, Class<T> type) {
        try {
            Field field = CheckStyleApplicationConfigurable.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return type.cast(field.get(configurable));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read field: " + fieldName, e);
        }
    }
}
