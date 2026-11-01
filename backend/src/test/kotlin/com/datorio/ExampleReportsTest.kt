package com.datorio

import com.datorio.model.dto.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class ExampleReportsTest {

    // examples/load_examples.py imports these files through POST /api/templates/import.
    private val reports: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("examples").resolve("reports") }
        .first { Files.isDirectory(it) }

    @Test
    fun `example reports match the import format and use only their own parameters`() {
        val mapper = jacksonObjectMapper()
        val files = Files.list(reports).use { s -> s.filter { it.toString().endsWith(".json") }.sorted().toList() }
        assertEquals(3, files.size)
        files.forEach { file ->
            val config = mapper.readValue<ReportExportConfig>(file.toFile())
            val names = config.parameters.map { it.name }.toSet()
            assertTrue(config.widgets.isNotEmpty(), "${file.fileName} has no widgets")
            config.parameterControls.forEach { assertTrue(it.parameterName in names, "${file.fileName}: ${it.parameterName}") }
            config.widgets.forEach { w ->
                val where = "${file.fileName} / ${w.title ?: w.widgetType}"
                mapper.readTree(w.position)
                val chart = mapper.readValue<Map<String, Any?>>(w.chartConfig)
                val mapping = mapper.readValue<Map<String, String>>(w.paramMapping)
                assertTrue((mapping.keys - "none").all { it in names }, "$where: $mapping")
                @Suppress("UNCHECKED_CAST")
                val jobParams = chart["jobParams"] as? List<String> ?: emptyList()
                assertTrue(jobParams.all { it in names }, "$where: $jobParams")
            }
            // A drill-replace names its widgets by sortOrder, reads a column of the source query and sets a parameter
            val bySortOrder = config.widgets.associateBy { it.sortOrder }
            config.actions.forEach { a ->
                val where = "${file.fileName} / ${a.name}"
                val source = bySortOrder[a.sourceWidgetSortOrder]
                assertNotNull(source, where)
                assertTrue(a.targetWidgetSortOrders!!.split(",").all { it.toInt() in bySortOrder }, where)
                assertTrue(source!!.rawSql!!.contains("AS `${a.sourceField}`"), "$where: ${a.sourceField}")
                assertTrue(mapper.readTree(a.config)["paramName"].asText() in names, where)
            }
        }
    }
}
