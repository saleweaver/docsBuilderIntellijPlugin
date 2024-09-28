package org.datastic.docsbuilder

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.getUserData
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.JPanel


class ContextMenuAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        // Get the selected text
        val editor = e.getData(CommonDataKeys.EDITOR)
        val project = e.project
        val selectedText = editor?.selectionModel?.selectedText

        if (selectedText != null) {
            // Send the selected text to ChatGPTService
            ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Generating Documentation") {
                override fun run(indicator: ProgressIndicator) {
                    val chatGPTService = ChatGPTService

                    val documentationType = PluginSettings.instance.state.documentationType

                    val prompt = when (documentationType) {
                        DocumentationType.DOCSTRINGS -> {
                            "Provide a concise documentation for the following code. Add the documentation to the code directly, keeping the original code intact and unchanged, only adding documentation. Please do not put the code inside triple backticks. Make sure that the code can be copied as is, comment non code properly.:\n\n$selectedText"
                        }

                        DocumentationType.DETAILED_DOCSTRINGS -> {
                            "Provide a detailed documentation for the following code, explaining what each part does, including loops, conditionals, and other constructs. Add the documentation to the code directly, keeping the original code intact and unchanged, only adding documentation. Please do not put the code inside triple backticks. Make sure that the code can be copied as is, comment non code properly.\n\n$selectedText"
                        }

                        DocumentationType.EXTERNAL_FILE -> {
                            // Handle external file documentation if necessary
                            "Provide a concise documentation for the following code. Create a md file to document it.:\n\n$selectedText"
                        }
                    }
                    val response = chatGPTService.generateDocumentation(prompt)

                    // Display the result in the Documentation Creator ToolWindow
                    if (response != null) {
                        val cleanedText = response.replace(Regex("^```.*$", RegexOption.MULTILINE), "").trim()

                        val project = e.project
                        if (project != null) {
                            ApplicationManager.getApplication().invokeLater {
                                WriteCommandAction.runWriteCommandAction(project) {
                                    val toolWindow =
                                        ToolWindowManager.getInstance(project).getToolWindow("Documentation Creator")
                                    toolWindow?.show {
                                        // Get the panel and retrieve the stored editor via UserData
                                        val content = toolWindow.contentManager.getContent(0)?.component
                                        if (content is JPanel) {
                                            val editor = content.getUserData(ChatGPTToolWindowFactory.EDITOR_KEY)
                                            if (editor != null) {
                                                // Update the editor with the response text
                                                ApplicationManager.getApplication()
                                                    .runWriteAction {
                                                        editor.document.setText(cleanedText)
                                                    }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Messages.showMessageDialog(
                                e.project,
                                "No project available!",
                                "Error",
                                Messages.getErrorIcon()
                            )
                        }
                    } else {
                        Messages.showMessageDialog(
                            e.project,
                            "Failed to get a response from ChatGPT",
                            "Error",
                            Messages.getErrorIcon()
                        )
                    }
                }
            })
        }
    }

    override fun update(e: AnActionEvent) {
        // Show the action only if text is selected
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isVisible = editor?.selectionModel?.hasSelection() == true
    }
}