// src/main/kotlin/org/datastic/docsbuilder/DocumentationGenerator.kt

package org.datastic.docsbuilder

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager

class DocumentationGenerator(private val project: Project, private val editor: Editor) {

    fun generateDocumentation() {
        // Run the API call in a background task
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Documentation") {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                try {
                    // Fetch user preferences
                    val selectedModel = PluginSettings.instance.state.selectedModel
                    val documentationType = PluginSettings.instance.state.documentationType

                    // Extract code from the main editor (not the tool window's editor)
                    val mainEditor = FileEditorManager.getInstance(project).selectedTextEditor
                    val codeToDocumentate = mainEditor?.document?.text

                    if (codeToDocumentate.isNullOrBlank()) {
                        ApplicationManager.getApplication().invokeLater {
                            Messages.showErrorDialog(project, "No code found in the active editor to document.", "Error")
                        }
                        return
                    }

                    // Generate the appropriate prompt based on user preference
                    val prompt = when (documentationType) {
                        DocumentationType.DOCSTRINGS -> {
                            "Provide a concise documentation for the following code. Add the documentation to the code directly, keeping the original code intact and unchanged, only adding documentation. Please do not put the code inside triple backticks. Make sure that the code can be copied as is, comment non code properly.:\n\n$codeToDocumentate"
                        }
                        DocumentationType.DETAILED_DOCSTRINGS -> {
                            "Provide a detailed documentation for the following code, explaining what each part does, including loops, conditionals, and other constructs. Add the documentation to the code directly, keeping the original code intact and unchanged, only adding documentation. Please do not put the code inside triple backticks. Make sure that the code can be copied as is, comment non code properly.\n\n$codeToDocumentate"
                        }
                        DocumentationType.EXTERNAL_FILE -> {
                            // Handle external file documentation if necessary
                            "Provide a concise documentation for the following code. Create a md file to document it.:\n\n$codeToDocumentate"
                        }
                    }

                    // Generate documentation using ChatGPTService
                    val documentation = ChatGPTService.generateDocumentation(prompt)

                    // Update the tool window's editor's document within a write command
                    ApplicationManager.getApplication().invokeLater {
                        WriteCommandAction.runWriteCommandAction(project) {
                            editor.document.setText(documentation)
                        }
                        Messages.showInfoMessage(project, "Documentation generated successfully.", "Success")
                    }
                } catch (e: Exception) {
                    // Handle exceptions and show an error dialog
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(project, "Failed to generate documentation: ${e.message}", "Error")
                    }
                }
            }
        })
    }
}