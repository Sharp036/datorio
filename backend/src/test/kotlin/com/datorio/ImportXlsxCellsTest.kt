package com.datorio

import com.datorio.model.*
import com.datorio.repository.*
import com.datorio.service.*
import org.apache.poi.util.LocaleUtil
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.*
import org.springframework.mock.web.MockMultipartFile
import java.io.ByteArrayOutputStream
import java.util.*

class ImportXlsxCellsTest {

    @AfterEach
    fun resetLocale() = LocaleUtil.setUserLocale(null)

    @ParameterizedTest
    @ValueSource(strings = ["en-US", "ru-RU"])
    fun `cells are read as values whatever their number format and the server locale`(locale: String) {
        LocaleUtil.setUserLocale(Locale.forLanguageTag(locale))
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("Data")
        val formats = workbook.createDataFormat()
        fun style(format: String) = workbook.createCellStyle().apply { dataFormat = formats.getFormat(format) }
        val header = sheet.createRow(0)
        val row = sheet.createRow(1)
        listOf("Amount", "Count", "Share", "Code", "Total", "Date", "Name", "Flag", "Broken")
            .forEachIndexed { i, name -> header.createCell(i).setCellValue(name) }
        row.createCell(0).apply { setCellValue(1234.56); cellStyle = style("#,##0.00") }
        row.createCell(1).apply { setCellValue(12345.0); cellStyle = style("#,##0") }
        row.createCell(2).apply { setCellValue(0.15); cellStyle = style("0%") }
        row.createCell(3).apply { setCellValue(1234.0); cellStyle = style("00000") }
        row.createCell(4).apply { cellFormula = "A2*2"; cellStyle = style("#,##0.00") }
        row.createCell(5).apply { setCellValue(java.time.LocalDate.of(2026, 1, 15)); cellStyle = style("dd.mm.yyyy") }
        row.createCell(6).setCellValue("Laptop")
        row.createCell(7).setCellValue(true)
        row.createCell(8).cellFormula = "1/0"
        XSSFFormulaEvaluator.evaluateAllFormulaCells(workbook)
        // Added after the calculation, so the file keeps no result for it
        header.createCell(9).setCellValue("Uncalculated")
        row.createCell(9).cellFormula = "B2+1"
        val bytes = ByteArrayOutputStream().also { workbook.write(it) }.toByteArray()

        val source = ImportSource(id = 1L, name = "cells", datasource = mock(), sourceFormat = "xlsx",
            targetSchema = "s", targetTable = "t")
        val sourceRepo: ImportSourceRepository = mock()
        whenever(sourceRepo.findById(1L)).thenReturn(Optional.of(source))
        val service = ImportService(sourceRepo, mock(), mock(), mock(), mock(), mock())

        val preview = service.preview(1L, MockMultipartFile("file", "cells.xlsx", null, bytes))

        assertEquals(listOf("1234.56", "12345", "0.15", "01234", "2469.12", "2026-01-15", "Laptop", "true", "#DIV/0!",
            "12346"), preview.rows.single())
    }
}
