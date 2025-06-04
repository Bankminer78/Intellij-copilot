package com.github.bankminer78.intellijcopilot

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ide.util.PropertiesComponent
import com.intellij.util.ui.FormBuilder
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JPasswordField
import javax.swing.JPanel

class ChatConfigurable : SearchableConfigurable {
    companion object {
        const val ID = "com.github.bankminer78.intellijcopilot.OpenAIConfigurable"
        private const val PREF_KEY = "intellijCopilot.apiKey"
    }

    private var apiKeyField: JPasswordField? = null
    private var mainPanel: JPanel? = null

    override fun getId(): String = ID

    @Nls(capitalization = Nls.Capitalization.Title)
    override fun getDisplayName(): String = "Copilot API Key"

    override fun createComponent(): JComponent {
        apiKeyField = JPasswordField().apply {
            val saved = PropertiesComponent.getInstance().getValue(PREF_KEY, "")
            text = saved
        }
        val explanatoryLabel = JBLabel(
            "<html>" +
                    "Paste your OpenAI API key here so the plugin can call GPT.<br/>" +
                    "It is stored securely by IntelliJ and not committed anywhere." +
                    "</html>"
        )
        mainPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("OpenAI API Key:", apiKeyField!!)
            .addComponent(explanatoryLabel)
            .panel
        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val stored = PropertiesComponent.getInstance().getValue(PREF_KEY, "")
        val current = apiKeyField?.text ?: ""
        return stored != current
    }

    override fun apply() {
        val newKey = apiKeyField?.text ?: ""
        PropertiesComponent.getInstance().setValue(PREF_KEY, newKey)
    }

    override fun reset() {
        val stored = PropertiesComponent.getInstance().getValue(PREF_KEY, "")
        apiKeyField?.text = stored
    }

    override fun disposeUIResources() {
        apiKeyField = null
        mainPanel = null
    }
}