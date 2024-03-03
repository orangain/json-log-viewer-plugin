package com.github.orangain.jsonlogviewerplugin.providers

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.awt.Dimension


class MyConsoleFilterProvider : ConsoleFilterProvider {
    override fun getDefaultFilters(project: Project): Array<Filter> {
        return arrayOf(MyConsoleFilter())
    }
}

class MyConsoleFilter : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        thisLogger().debug("entireLength: $entireLength, applyFilter: $line")
        val json = extractJson(line) ?: return null

        val startIndex = entireLength - line.length
        return Filter.Result(
            startIndex,
            startIndex + "[Detail]".length,
            MyHyperlinkInfo(json),
        )
    }
}

private val jsonPattern = Regex("""^\[Detail]\s*(\{.*})\s*$""")
private val mapper = jacksonObjectMapper().apply {
    configure(SerializationFeature.INDENT_OUTPUT, true)
    configure(JsonNodeFeature.WRITE_PROPERTIES_SORTED, true)
}

private fun extractJson(text: String): String? {
    return jsonPattern.find(text)?.groups?.get(1)?.value
}

class MyHyperlinkInfo(private val json: String) : HyperlinkInfo {
    override fun navigate(project: Project) {
        val node = mapper.readTree(json)
        val jsonString = mapper.writeValueAsString(node)
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
        val popup = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
                "<pre style='white-space: pre-wrap'><code>$jsonString</code></pre>",
                null,
                MessageType.INFO.popupBackground,
                null
            )
            .setPointerSize(Dimension(1, 1)) // Don't show the arrow
            .setCloseButtonEnabled(true)
            .createBalloon()

        val contentManager = RunContentManager.getInstance(project)
        val console = contentManager.selectedContent?.executionConsole?.component ?: return
        popup.showInCenterOf(console)
    }
}
