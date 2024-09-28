// src/main/kotlin/org/datastic/docsbuilder/PluginSettings.kt

package org.datastic.docsbuilder

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service

@State(
    name = "PluginSettings",
    storages = [Storage("docsbuilder.xml")]
)
@Service
class PluginSettings : PersistentStateComponent<PluginSettings.State> {

    data class State(
        var apiKey: String = "",
        var selectedModel: String = "gpt-4",
        var documentationType: DocumentationType = DocumentationType.DOCSTRINGS,
        var detailedDocumentation: Boolean = false,
        var maxTokens: Int = 500,
        var temperature: Double = 0.5
    )

    private var _state = State()

    override fun getState(): State = _state

    override fun loadState(state: State) {
        _state = state
    }

    companion object {
        val instance: PluginSettings
            get() = service<PluginSettings>()
    }
}