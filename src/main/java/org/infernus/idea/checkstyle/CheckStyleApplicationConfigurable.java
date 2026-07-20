package org.infernus.idea.checkstyle;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.infernus.idea.checkstyle.checker.CheckerFactoryCache;
import org.infernus.idea.checkstyle.config.ApplicationConfigurationState;
import org.infernus.idea.checkstyle.config.ApplicationConfigurationState.GlobalConfigurationLocation;
import org.infernus.idea.checkstyle.ui.GlobalLocationDialogue;
import org.infernus.idea.checkstyle.ui.GlobalLocationTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Objects;


/**
 * The application-level (IDE-wide) "configurable component" for CheckStyle plugin settings that are not
 * scoped to a single project, currently just the artifact download mirror override. Registered in
 * {@code plugin.xml} as an {@code applicationConfigurable} extension.
 */
public class CheckStyleApplicationConfigurable implements Configurable {

    private static final Dimension DECORATOR_DIMENSIONS = new Dimension(300, 150);

    private final ApplicationConfigurationState applicationConfigurationState;

    private JTextField artifactRepositoryBaseUrlOverrideField;

    private JCheckBox useGlobalRulesByDefaultCheckbox;

    private final GlobalLocationTableModel globalLocationTableModel = new GlobalLocationTableModel();

    private JBTable globalLocationTable;

    public CheckStyleApplicationConfigurable() {
        this(ApplicationManager.getApplication().getService(ApplicationConfigurationState.class));
    }

    CheckStyleApplicationConfigurable(@NotNull final ApplicationConfigurationState applicationConfigurationState) {
        this.applicationConfigurationState = applicationConfigurationState;
    }

    @Nls
    @Override
    public String getDisplayName() {
        return CheckStyleBundle.message("config.application.configuration-name");
    }

    @Override
    public JComponent createComponent() {
        artifactRepositoryBaseUrlOverrideField = new JTextField();
        artifactRepositoryBaseUrlOverrideField.setToolTipText(
                CheckStyleBundle.message("config.artefact-repository-base-url-override.tooltip"));

        useGlobalRulesByDefaultCheckbox = new JCheckBox(
                CheckStyleBundle.message("config.global.use-by-default.text"));
        useGlobalRulesByDefaultCheckbox.setToolTipText(
                CheckStyleBundle.message("config.global.use-by-default.tooltip"));

        globalLocationTable = new JBTable(globalLocationTableModel);
        globalLocationTable.setStriped(true);
        globalLocationTable.getTableHeader().setReorderingAllowed(false);

        reset();

        final JTextArea description = new JTextArea(CheckStyleBundle.message("config.artefact-repository-base-url-override.description"));
        description.setFont(UIManager.getFont("Label.font"));
        description.setEditable(false);
        description.setOpaque(false);
        description.setWrapStyleWord(true);
        description.setLineWrap(true);

        final ToolbarDecorator tableDecorator = ToolbarDecorator.createDecorator(globalLocationTable);
        tableDecorator.setAddAction(button -> {
            final GlobalLocationDialogue dialogue = new GlobalLocationDialogue(null);
            if (dialogue.showAndGet()) {
                final GlobalConfigurationLocation newLocation = dialogue.getGlobalConfigurationLocation();
                if (newLocation != null) {
                    globalLocationTableModel.addLocation(newLocation);
                }
            }
        });
        tableDecorator.setEditAction(button -> {
            final int selectedRow = globalLocationTable.getSelectedRow();
            if (selectedRow >= 0) {
                final GlobalLocationDialogue dialogue = new GlobalLocationDialogue(
                        globalLocationTableModel.getLocationAt(selectedRow));
                if (dialogue.showAndGet()) {
                    final GlobalConfigurationLocation updated = dialogue.getGlobalConfigurationLocation();
                    if (updated != null) {
                        globalLocationTableModel.updateLocationAt(selectedRow, updated);
                    }
                }
            }
        });
        tableDecorator.setRemoveAction(button -> {
            final int selectedRow = globalLocationTable.getSelectedRow();
            if (selectedRow >= 0) {
                globalLocationTableModel.removeLocationAt(selectedRow);
            }
        });
        tableDecorator.setPreferredSize(DECORATOR_DIMENSIONS);

        return FormBuilder.createFormBuilder()
                .addComponent(description)
                .addLabeledComponent(
                        CheckStyleBundle.message("config.artefact-repository-base-url-override.label.text"),
                        artifactRepositoryBaseUrlOverrideField)
                .addComponent(new TitledSeparator(CheckStyleBundle.message("config.global.section.title")))
                .addComponent(useGlobalRulesByDefaultCheckbox)
                .addLabeledComponent(
                        CheckStyleBundle.message("config.global.locations.label"),
                        tableDecorator.createPanel(),
                        JBUI.scale(4),
                        true)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        return !Objects.equals(
                normalise(artifactRepositoryBaseUrlOverrideField.getText()),
                normalise(applicationConfigurationState.getArtifactRepositoryBaseUrlOverride()))
                || useGlobalRulesByDefaultCheckbox.isSelected() != applicationConfigurationState.isUseGlobalRulesByDefault()
                || !globalLocationTableModel.getLocations().equals(applicationConfigurationState.getGlobalLocations())
                || !new HashSet<>(globalLocationTableModel.getActiveIds()).equals(
                        new HashSet<>(applicationConfigurationState.getActiveGlobalLocationIds()));
    }

    @Override
    public void apply() {
        applicationConfigurationState.setArtifactRepositoryBaseUrlOverride(
                normalise(artifactRepositoryBaseUrlOverrideField.getText()));
        applicationConfigurationState.setUseGlobalRulesByDefault(useGlobalRulesByDefaultCheckbox.isSelected());
        applicationConfigurationState.setGlobalLocations(globalLocationTableModel.getLocations());
        applicationConfigurationState.setActiveGlobalLocationIds(globalLocationTableModel.getActiveIds());

        // Invalidate checker caches in all open projects so stale global-location checkers are evicted.
        final ProjectManager projectManager = ProjectManager.getInstanceIfCreated();
        if (projectManager != null) {
            for (final Project project : projectManager.getOpenProjects()) {
                project.getService(CheckerFactoryCache.class).invalidate();
            }
        }
    }

    @Override
    public void reset() {
        artifactRepositoryBaseUrlOverrideField.setText(
                Objects.requireNonNullElse(applicationConfigurationState.getArtifactRepositoryBaseUrlOverride(), ""));
        useGlobalRulesByDefaultCheckbox.setSelected(applicationConfigurationState.isUseGlobalRulesByDefault());
        globalLocationTableModel.setLocations(
                applicationConfigurationState.getGlobalLocations(),
                applicationConfigurationState.getActiveGlobalLocationIds());
    }

    JTextField getArtifactRepositoryBaseUrlOverrideField() {
        return artifactRepositoryBaseUrlOverrideField;
    }

    @Nullable
    private static String normalise(@Nullable final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
