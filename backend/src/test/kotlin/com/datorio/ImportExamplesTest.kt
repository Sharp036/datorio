package com.datorio

import com.datorio.model.*
import com.datorio.repository.*
import com.datorio.service.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.mock.web.MockMultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class ImportExamplesTest {

    // The README points to these files for trying file imports on the sample data.
    private val examples: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .map { it.resolve("examples") }
        .first { Files.isDirectory(it) }

    @Test
    fun `sample sales workbook is read by the shipped import source`() {
        val json = jacksonObjectMapper().readTree(examples.resolve("sales-import-source.json").toFile())
        val source = ImportSource(
            id = 1L,
            name = json["name"].asText(),
            datasource = mock(),
            sourceFormat = json["sourceFormat"].asText(),
            sheetName = json["sheetName"].asText(),
            targetSchema = json["targetSchema"].asText(),
            targetTable = json["targetTable"].asText(),
            strictColumns = json["strictColumns"].asBoolean(),
        )
        json["mappings"].forEach { m ->
            source.mappings.add(ImportSourceMapping(
                source = source,
                sourceColumn = m["sourceColumn"].asText(),
                targetColumn = m["targetColumn"].asText(),
                dataType = m["dataType"].asText(),
                nullable = m["nullable"].asBoolean(),
                dateFormat = m["dateFormat"].takeUnless { it.isNull }?.asText(),
            ))
        }
        val sourceRepo: ImportSourceRepository = mock()
        whenever(sourceRepo.findById(1L)).thenReturn(Optional.of(source))
        val service = ImportService(sourceRepo, mock(), mock(), mock(), mock(), mock())
        val file = MockMultipartFile("file", "sales_2026_01.xlsx", null,
            Files.readAllBytes(examples.resolve("sales_2026_01.xlsx")))

        val preview = service.preview(1L, file)

        assertEquals(source.mappings.map { it.sourceColumn }, preview.columns)
        assertEquals(5, preview.rows.size)
        // Every value parses the way the import parses it for its column type.
        preview.rows.forEach { row ->
            source.mappings.zip(row).forEach { (m, cell) ->
                val value = assertInstanceOf(String::class.java, cell, "${m.sourceColumn} is empty")
                when (m.dataType) {
                    "date" -> LocalDate.parse(value, DateTimeFormatter.ofPattern(m.dateFormat))
                    "integer" -> assertNotNull(value.toLongOrNull(), "${m.sourceColumn} = $value")
                    "float" -> assertNotNull(value.toDoubleOrNull(), "${m.sourceColumn} = $value")
                }
            }
        }
        assertEquals(listOf("2026-01-01", "20260101001"), preview.rows.first().take(2))
    }
}
