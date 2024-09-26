// src/main/kotlin/org/datastic/docsbuilder/SettingsConfigurable.kt

package org.datastic.docsbuilder

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import javax.swing.*
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.border.EmptyBorder
import kotlin.concurrent.thread

class SettingsConfigurable : Configurable {

    private var panel: JPanel? = null
    private var apiKeyField: JTextField? = null
    private var modelComboBox: JComboBox<String>? = null
    private var documentationTypeComboBox: JComboBox<String>? = null
    private var loadModelsButton: JButton? = null
    private var isLoadingModels: Boolean = false

    override fun getDisplayName(): String = "Documentation Builder"

    override fun createComponent(): JComponent {
        panel = JPanel(GridBagLayout())
        panel!!.border = EmptyBorder(10, 10, 10, 10)

        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST

        // Row 0: API Key Label
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 0.0
        panel!!.add(JLabel("OpenAI API Key:"), gbc)

        // Row 0: API Key Field
        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 1.0
        apiKeyField = JTextField(30)
        apiKeyField!!.text = PluginSettings.instance.state.apiKey
        panel!!.add(apiKeyField, gbc)

        // Row 1: Model Selection Label
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.weightx = 0.0
        panel!!.add(JLabel("Select Model:"), gbc)

        // Row 1: Model ComboBox and Load Button
        gbc.gridx = 1
        gbc.gridy = 1
        gbc.weightx = 1.0
        val modelPanel = JPanel(BorderLayout(5, 0))
        modelComboBox = JComboBox()
        modelComboBox!!.isEnabled = false // Initially disabled until API key is set
        modelPanel.add(modelComboBox, BorderLayout.CENTER)
        loadModelsButton = JButton("Load Models")
        modelPanel.add(loadModelsButton, BorderLayout.EAST)
        panel!!.add(modelPanel, gbc)

        // Row 2: Documentation Type Label
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.weightx = 0.0
        panel!!.add(JLabel("Documentation Type:"), gbc)

        // Row 2: Documentation Type ComboBox
        gbc.gridx = 1
        gbc.gridy = 2
        gbc.weightx = 1.0
        documentationTypeComboBox = ComboBox(
            arrayOf("Docstrings", "External File", "Detailed Docstrings")
        )
        documentationTypeComboBox!!.selectedIndex =
            if (PluginSettings.instance.state.documentationType == DocumentationType.DOCSTRINGS) 0 else
                    (if (PluginSettings.instance.state.documentationType == DocumentationType.EXTERNAL_FILE) 1 else 2)
        panel!!.add(documentationTypeComboBox, gbc)

        // Row 3: Placeholder (Removed Detailed Documentation Checkbox)

        // Row 4: Save Button (Removed)

        // Add Action Listeners
        loadModelsButton!!.addActionListener {
            loadAvailableModels()
        }

        // Enable model selection only if API key is present
        modelComboBox!!.isEnabled = PluginSettings.instance.state.apiKey.isNotBlank()
        loadModelsButton!!.isEnabled = PluginSettings.instance.state.apiKey.isNotBlank()

        // Automatically load models if API key is already set
        if (PluginSettings.instance.state.apiKey.isNotBlank()) {
            loadAvailableModels()
        }

        return panel!!
    }

    private fun saveSettings() {
        val apiKey = apiKeyField!!.text.trim()
        if (apiKey.isEmpty()) {
            Messages.showErrorDialog(panel, "API Key cannot be empty.", "Validation Error")
            return
        }
        PluginSettings.instance.state.apiKey = apiKey

        // Save selected model
        val selectedModel = modelComboBox?.selectedItem?.toString() ?: "gpt-4"
        PluginSettings.instance.state.selectedModel = selectedModel

        // Save documentation type
        val selectedDocType = when (documentationTypeComboBox?.selectedIndex) {
            0 -> DocumentationType.DOCSTRINGS
            1 -> DocumentationType.EXTERNAL_FILE
            2 -> DocumentationType.DETAILED_DOCSTRINGS
            else -> DocumentationType.DOCSTRINGS
        }
        PluginSettings.instance.state.documentationType = selectedDocType

        Messages.showInfoMessage(panel, "Settings saved successfully.", "Success")

        // Automatically load models after saving API key
        loadAvailableModels()
    }

    private fun loadAvailableModels() {
        if (isLoadingModels) return // Prevent multiple concurrent loads

        val apiKey = apiKeyField!!.text.trim()
        if (apiKey.isEmpty()) {
            Messages.showErrorDialog(panel, "Please enter a valid API Key first.", "Error")
            return
        }

        isLoadingModels = true
        loadModelsButton!!.text = "Loading..."
        modelComboBox!!.isEnabled = false

        thread {
            val models = ChatGPTService.fetchAvailableModels(apiKey)
            SwingUtilities.invokeLater {
                modelComboBox!!.removeAllItems()
                if (models.isNotEmpty()) {
                    models.forEach { model ->
                        modelComboBox!!.addItem(model)
                    }
                    modelComboBox!!.selectedItem = PluginSettings.instance.state.selectedModel
                    modelComboBox!!.isEnabled = true
                } else {
                    Messages.showErrorDialog(panel, "Failed to load models. Please check your API Key and network connection.", "Error")
                }
                loadModelsButton!!.text = "Load Models"
                isLoadingModels = false
            }
        }
    }

    override fun isModified(): Boolean {
        val currentApiKey = apiKeyField?.text?.trim() ?: ""
        val currentSelectedModel = modelComboBox?.selectedItem?.toString() ?: ""
        val currentDocType = when (documentationTypeComboBox?.selectedIndex) {
            0 -> DocumentationType.DOCSTRINGS
            1 -> DocumentationType.EXTERNAL_FILE
            2 -> DocumentationType.DETAILED_DOCSTRINGS
            else -> DocumentationType.DOCSTRINGS
        }

        return currentApiKey != PluginSettings.instance.state.apiKey ||
                currentSelectedModel != PluginSettings.instance.state.selectedModel ||
                currentDocType != PluginSettings.instance.state.documentationType
    }

    override fun apply() {
        saveSettings()
    }

    override fun reset() {
        apiKeyField?.text = PluginSettings.instance.state.apiKey
        documentationTypeComboBox?.selectedIndex =
             if (PluginSettings.instance.state.documentationType == DocumentationType.DOCSTRINGS) 0 else
                    (if (PluginSettings.instance.state.documentationType == DocumentationType.EXTERNAL_FILE) 1 else 2)
        modelComboBox?.removeAllItems()
        if (PluginSettings.instance.state.apiKey.isNotBlank()) {
            loadAvailableModels()
        }
    }
}