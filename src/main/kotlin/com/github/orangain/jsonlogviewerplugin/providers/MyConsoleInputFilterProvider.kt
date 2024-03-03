package com.github.orangain.jsonlogviewerplugin.providers

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.intellij.execution.filters.ConsoleInputFilterProvider
import com.intellij.execution.filters.InputFilter
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair

class MyConsoleInputFilterProvider : ConsoleInputFilterProvider {
    override fun getDefaultFilters(project: Project): Array<InputFilter> {
        return arrayOf(MyConsoleInputFilter())
    }
}

class MyConsoleInputFilter : InputFilter {
    override fun applyFilter(
        text: String,
        contentType: ConsoleViewContentType
    ): MutableList<Pair<String, ConsoleViewContentType>>? {
        thisLogger().debug("contentType: $contentType, applyFilter: $text")
        val node = parseJson(text) ?: return null

        val severity = node.get("severity")?.asText()?.uppercase() ?: "DEFAULT"
        return mutableListOf(Pair("[Detail] $text", contentTypeOf(severity, contentType)))
    }
}

private val jsonPattern = Regex("""^\s*\{.*}\s*$""")
private val mapper = jacksonObjectMapper()

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

private fun contentTypeOf(severity: String, inputContentType: ConsoleViewContentType): ConsoleViewContentType {
    return when (severity) {
        "DEBUG" -> ConsoleViewContentType.LOG_DEBUG_OUTPUT
        "INFO", "NOTICE" -> ConsoleViewContentType.LOG_INFO_OUTPUT
        "WARNING" -> ConsoleViewContentType.LOG_WARNING_OUTPUT
        "ERROR", "CRITICAL",  "ALERT", "EMERGENCY" -> ConsoleViewContentType.LOG_ERROR_OUTPUT
        else -> inputContentType
    }
}