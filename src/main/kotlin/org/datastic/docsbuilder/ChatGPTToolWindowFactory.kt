// src/main/kotlin/org/datastic/docsbuilder/ChatGPTToolWindowFactory.kt

package org.datastic.docsbuilder

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JPanel
import javax.swing.JButton
import javax.swing.JScrollPane
import javax.swing.JPopupMenu
import javax.swing.JMenuItem
import java.awt.BorderLayout
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.application.WriteAction
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.io.File

class ChatGPTToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panel = JPanel(BorderLayout())

        // Initialize Editor for Syntax Highlighting
        val editor = createEditor(project)
        val editorScrollPane = JScrollPane(editor.component)
        panel.add(editorScrollPane, BorderLayout.CENTER)

        // Button Panel at the Top
        val buttonPanel = JPanel(BorderLayout())

        // Generate Documentation Button
        val generateButton = JButton("Generate Documentation")
        generateButton.toolTipText = "Click to generate documentation for the current file"
        buttonPanel.add(generateButton, BorderLayout.WEST)

        // Copy/Write Dropdown Button
        val copyWriteButton = JButton("Copy")
        copyWriteButton.toolTipText = "Click to copy or write the documentation to the file"
        val popupMenu = JPopupMenu()
        val copyItem = JMenuItem("Copy")

        popupMenu.add(copyItem)

        // Add Mouse Listener for Dropdown Behavior
        copyWriteButton.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent?) {
                if (e?.isPopupTrigger == true) {
                    popupMenu.show(e.component, e.x, e.y)
                } else {
                    // Default Action: Copy
                    copyDocumentation(editor, project)
                }
            }

            override fun mouseReleased(e: MouseEvent?) {
                if (e?.isPopupTrigger == true) {
                    popupMenu.show(e.component, e.x, e.y)
                }
            }
        })

        // Add Action Listeners for Menu Items
        copyItem.addActionListener {
            copyDocumentation(editor, project)
        }

        // Initially, hide the Copy/Write Button unless DocumentationType is DOCSTRINGS or DETAILED_DOCSTRINGS
        val documentationType = PluginSettings.instance.state.documentationType
        if (documentationType == DocumentationType.DOCSTRINGS || documentationType == DocumentationType.DETAILED_DOCSTRINGS) {
            buttonPanel.add(copyWriteButton, BorderLayout.EAST)
        }

        panel.add(buttonPanel, BorderLayout.NORTH)

        // Add Action Listener for Generate Button
        generateButton.addActionListener {
            val generator = DocumentationGenerator(project, editor)
            generator.generateDocumentation()
            // After documentation is generated, decide to show the Copy/Write button
            val currentDocType = PluginSettings.instance.state.documentationType
            if (currentDocType == DocumentationType.DOCSTRINGS || currentDocType == DocumentationType.DETAILED_DOCSTRINGS) {
                if (buttonPanel.componentCount == 1) { // Only Generate Button is present
                    buttonPanel.add(copyWriteButton, BorderLayout.EAST)
                    panel.revalidate()
                    panel.repaint()
                }
            } else {
                if (buttonPanel.componentCount > 1) { // Generate + CopyWrite
                    buttonPanel.remove(copyWriteButton)
                    panel.revalidate()
                    panel.repaint()
                }
            }
        }

        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    /**
     * Creates an Editor instance with syntax highlighting based on the current file type.
     */
    private fun createEditor(project: Project): Editor {
        val editorFactory = EditorFactory.getInstance()
        val virtualFile = FileEditorManager.getInstance(project).selectedTextEditor?.virtualFile

        val fileType = virtualFile?.let {
            FileTypeManager.getInstance().getFileTypeByFile(it)
        } ?: FileTypeManager.getInstance().getFileTypeByExtension("txt") // Default to plain text

        val editor = editorFactory.createViewer(
            EditorFactory.getInstance().createDocument(""), // Empty document initially
            project,
        )
        editor.settings.isLineNumbersShown = true // Optional: Show line numbers
        editor.settings.isFoldingOutlineShown = true // Optional: Show folding outlines
        editor.settings.isWhitespacesShown = true // Optional: Show whitespaces

        return editor
    }

    /**
     * Copies the documentation from the editor to the clipboard.
     */
    private fun copyDocumentation(editor: Editor, project: Project) {
        val documentation = editor.document.text
        val stringSelection = StringSelection(documentation)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(stringSelection, null)
        Messages.showInfoMessage(project, "Documentation copied to clipboard.", "Success")
    }


}