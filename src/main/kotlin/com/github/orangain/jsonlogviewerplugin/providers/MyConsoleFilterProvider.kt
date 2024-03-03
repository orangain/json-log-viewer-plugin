package com.github.orangain.jsonlogviewerplugin.providers

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.markup.TextAttributes
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
        val node = parseJson(line) ?: return null

        val textAttributes = textAttributesOf(node.get("severity")?.asText())
        return Filter.Result(
            entireLength - line.length,
            entireLength,
            MyHyperlinkInfo(node),
            textAttributes,
            textAttributes,
        )
    }
}

val jsonPattern = Regex("""^\s*\{.*}\s*$""")
val mapper = jacksonObjectMapper()

fun parseJson(text: String): JsonNode? {
    if (!jsonPattern.matches(text)) {
        return null
    }
    return try {
        mapper.readTree(text)
    } catch (e: JsonProcessingException) {
        null
    }
}

fun textAttributesOf(severity: String?): TextAttributes {
    return when (severity) {
        "ERROR" -> CodeInsightColors.ERRORS_ATTRIBUTES.defaultAttributes
        "WARN" -> CodeInsightColors.WARNINGS_ATTRIBUTES.defaultAttributes
        "INFO" -> CodeInsightColors.INFORMATION_ATTRIBUTES.defaultAttributes
        else -> CodeInsightColors.INFORMATION_ATTRIBUTES.defaultAttributes
    }
}

class MyHyperlinkInfo(private val node: JsonNode) : HyperlinkInfo {
    override fun navigate(project: Project) {
        val popup = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder("<pre><code>${node.toPrettyString()}</code></pre>", MessageType.INFO, null)
            .setPointerSize(Dimension(1, 1)) // Don't show the arrow
            .createBalloon()

        val contentManager = RunContentManager.getInstance(project)
        val console = contentManager.selectedContent?.executionConsole?.component ?: return
        popup.showInCenterOf(console)
    }
}
