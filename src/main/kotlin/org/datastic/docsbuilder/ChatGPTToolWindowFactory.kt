// src/main/kotlin/org/datastic/docsbuilder/ChatGPTToolWindowFactory.kt

package org.datastic.docsbuilder

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.putUserData
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu

class ChatGPTToolWindowFactory : ToolWindowFactory {
    private var editor: Editor? = null // Make editor an instance variable for dynamic updates
    private var panel: JPanel = JPanel(BorderLayout())
    private var tw: ToolWindow? = null
    private var isSubscribed = false

    companion object {
        val EDITOR_KEY = Key.create<Editor>("ChatGPT.Editor")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        tw = toolWindow
        val contentFactory = ContentFactory.getInstance()

        if (! isSubscribed) {
            setupFileEditorListener(project)
        }
        // Initialize Editor for Syntax Highlighting
        editor = createEditor(project)


        val editorScrollPane = JBScrollPane(editor!!.component)
        panel.add(editorScrollPane, BorderLayout.CENTER)
        panel.putUserData(EDITOR_KEY, editor)

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
                    copyDocumentation(editor!!, project)
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
            copyDocumentation(editor!!, project)
        }

        // Initially, hide the Copy/Write Button unless DocumentationType is DOCSTRINGS or DETAILED_DOCSTRINGS
        val documentationType = PluginSettings.instance.state.documentationType
        if (documentationType == DocumentationType.DOCSTRINGS || documentationType == DocumentationType.DETAILED_DOCSTRINGS) {
            buttonPanel.add(copyWriteButton, BorderLayout.EAST)
        }

        panel.add(buttonPanel, BorderLayout.NORTH)

        // Add Action Listener for Generate Button
        generateButton.addActionListener {
            val generator = DocumentationGenerator(project, editor!!)
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

        try {
            val editor = editorFactory.createEditor(
                EditorFactory.getInstance().createDocument(""), // Empty document initially
                project,
                virtualFile!!,
                true
            )
            editor.settings.isLineNumbersShown = true // Optional: Show line numbers
            editor.settings.isFoldingOutlineShown = true // Optional: Show folding outlines
            editor.settings.isWhitespacesShown = true // Optional: Show whitespaces

            return editor
        } catch (e: NullPointerException) {
            val editor = editorFactory.createViewer(
                EditorFactory.getInstance().createDocument(""),
                project,
            )
            editor.settings.isLineNumbersShown = true // Optional: Show line numbers
            editor.settings.isFoldingOutlineShown = true // Optional: Show folding outlines
            editor.settings.isWhitespacesShown = true // Optional: Show whitespaces

            return editor
        }
    }

        /**
     * Sets up a listener to react when a file is opened in the IDE.
     */
    private fun setupFileEditorListener(project: Project) {
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    panel.removeAll()
                    createToolWindowContent(project, tw!!)
                }

                override fun selectionChanged(event: FileEditorManagerEvent) {
                    val selectedFile = event.newFile
                    selectedFile?.let {
                        panel.removeAll()
                        createToolWindowContent(project, tw!!)
                    }
                }
            }
        )
            isSubscribed = true
    }

    /**
     * Updates the content of the editor with the provided text.
     */
    fun updateEditorContent(response: String) {
        ApplicationManager.getApplication().invokeLater {
            if (editor != null) {
                ApplicationManager.getApplication().runWriteAction {
                    editor?.document?.setText(response)
                }
            }
        }
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