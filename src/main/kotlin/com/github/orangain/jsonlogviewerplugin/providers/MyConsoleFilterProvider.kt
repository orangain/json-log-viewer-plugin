package com.github.orangain.jsonlogviewerplugin.providers

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.filters.ConsoleFilterProvider
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.process.ConsoleHighlighter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.RunContentManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.markup.EffectType
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
    private val textAttributesPairCache = mutableMapOf<String, Pair<TextAttributes, TextAttributes>>()
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        thisLogger().debug("entireLength: $entireLength, applyFilter: $line")
        val node = parseJson(line) ?: return null

        val severity = node.get("severity")?.asText()?.uppercase() ?: "DEFAULT"
        val (textAttributes, visitedTextAttributes) = textAttributesPairOf(severity)
        return Filter.Result(
            entireLength - line.length,
            entireLength,
            MyHyperlinkInfo(node),
            textAttributes,
            visitedTextAttributes,
        )
    }

    private fun textAttributesPairOf(severity: String): Pair<TextAttributes, TextAttributes> {
        return textAttributesPairCache.getOrPut(severity) {
            Pair(textAttributesOf(severity), visitedTextAttributesOf(severity))
        }
    }
}

private val jsonPattern = Regex("""^\s*\{.*}\s*$""")
private val mapper = jacksonObjectMapper().apply {
    configure(SerializationFeature.INDENT_OUTPUT, true)
    configure(JsonNodeFeature.WRITE_PROPERTIES_SORTED, true)
}

private fun parseJson(text: String): JsonNode? {
    if (!jsonPattern.matches(text)) {
        return null
    }
    return try {
        mapper.readTree(text)
    } catch (e: JsonProcessingException) {
        null
    }
}

private fun textAttributesOf(severity: String): TextAttributes {
    // See: https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#LogSeverity
    return when (severity) {
        "DEBUG" -> ConsoleHighlighter.GRAY.defaultAttributes
        "INFO", "NOTICE" -> ConsoleViewContentType.NORMAL_OUTPUT_KEY.defaultAttributes
        "WARNING" -> ConsoleHighlighter.YELLOW.defaultAttributes
        "ERROR", "CRITICAL" -> ConsoleHighlighter.RED.defaultAttributes
        "ALERT", "EMERGENCY" -> ConsoleHighlighter.RED_BRIGHT.defaultAttributes
        else -> ConsoleViewContentType.NORMAL_OUTPUT_KEY.defaultAttributes
    }
}

private fun visitedTextAttributesOf(severity: String): TextAttributes {
    return textAttributesOf(severity).clone().apply {
        effectType = EffectType.LINE_UNDERSCORE
        effectColor = foregroundColor
    }
}

class MyHyperlinkInfo(private val node: JsonNode) : HyperlinkInfo {
    override fun navigate(project: Project) {
        val popup = JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
                "<pre><code>${mapper.writeValueAsString(node)}</code></pre>",
                MessageType.INFO,
                null
            )
            .setPointerSize(Dimension(1, 1)) // Don't show the arrow
            .createBalloon()

        val contentManager = RunContentManager.getInstance(project)
        val console = contentManager.selectedContent?.executionConsole?.component ?: return
        popup.showInCenterOf(console)
    }
}
