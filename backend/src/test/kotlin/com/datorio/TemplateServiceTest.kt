package com.datorio

import com.datorio.model.*
import com.datorio.model.dto.*
import com.datorio.repository.ReportRepository
import com.datorio.repository.SavedQueryRepository
import com.datorio.service.*
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.time.Instant
import java.util.*

class TemplateServiceTest {

    private lateinit var reportRepo: ReportRepository
    private lateinit var reportService: ReportService
    private lateinit var vizService: VisualizationService
    private lateinit var controlService: ControlService
    private lateinit var interactiveService: InteractiveDashboardService
    private lateinit var templateService: TemplateService
    private val mapper = jacksonObjectMapper()

    @BeforeEach
    fun setup() {
        reportRepo = mock()
        reportService = mock()
        vizService = mock()
        controlService = mock()
        interactiveService = mock()
        templateService = TemplateService(reportRepo, reportService, vizService, controlService, interactiveService, mapper, mock<SavedQueryRepository>())
    }

    private fun action(type: String, source: Long?, targets: String?) = DashboardActionResponse(
        id = 1, reportId = 7, name = "$type action", actionType = type, triggerType = "CLICK",
        sourceWidgetId = source, targetWidgetIds = targets, sourceField = "Category", targetField = null,
        targetReportId = if (type == "NAVIGATE") 99 else null, urlTemplate = null, isActive = true, sortOrder = 0,
        config = mapOf("paramName" to "category"), createdAt = Instant.now()
    )

    @Test
    fun `export names the widgets of actions and buttons by sortOrder, not by id`() {
        val report = Report(id = 7, name = "Sales")
        listOf(
            ReportWidget(id = 101, report = report, widgetType = WidgetType.CHART, title = "Revenue by category", sortOrder = 0),
            ReportWidget(id = 102, report = report, widgetType = WidgetType.CHART, title = "Products", sortOrder = 1),
            ReportWidget(id = 103, report = report, widgetType = WidgetType.BUTTON, title = "Toggle",
                chartConfig = """{"buttonType":"TOGGLE","toggleWidgetIds":[102,555]}""", sortOrder = 2)
        ).forEach { report.widgets.add(it) }
        whenever(reportRepo.findById(7)).thenReturn(Optional.of(report))
        whenever(interactiveService.getActiveActionsForReport(7)).thenReturn(listOf(
            action("DRILL_REPLACE", 101, "102"),
            action("FILTER", 101, "*,-102"),
            action("NAVIGATE", 101, null)
        ))

        val config = templateService.exportReport(7)

        assertEquals(listOf("DRILL_REPLACE", "FILTER"), config.actions.map { it.actionType })
        val drill = config.actions[0]
        assertEquals(0, drill.sourceWidgetSortOrder)
        assertEquals("1", drill.targetWidgetSortOrders)
        assertEquals("category", mapper.readTree(drill.config)["paramName"].asText())
        assertEquals("*,-1", config.actions[1].targetWidgetSortOrders)
        // A button keeps the widgets it toggles by sortOrder; an id of no widget of the report is dropped
        val button = mapper.readTree(config.widgets[2].chartConfig)
        assertNull(button["toggleWidgetIds"])
        assertEquals(listOf(1), button["toggleWidgetSortOrders"].map { it.asInt() })
    }

    @Test
    fun `import creates the actions and buttons with the ids of the new widgets`() {
        val widgets = listOf(
            WidgetExportConfig(WidgetType.CHART, "Revenue by category", null, "SELECT 1", "{}", "{}", "{}", "{}", 0, true),
            WidgetExportConfig(WidgetType.CHART, "Products", null, "SELECT 1", "{}", "{}", "{}", "{}", 1, true,
                visibilityRules = listOf(VisibilityRuleExportConfig("PARAMETER", "region", "NEQ", "All"))),
            WidgetExportConfig(WidgetType.BUTTON, "Toggle", null, null,
                """{"buttonType":"TOGGLE","toggleWidgetSortOrders":[1]}""", "{}", "{}", "{}", 2, true)
        )
        val config = ReportExportConfig(
            name = "Sales", description = null, reportType = ReportType.STANDARD, layout = "{}", settings = "{}",
            category = null, parameters = emptyList(), widgets = widgets,
            actions = listOf(
                ActionExportConfig("Show products", "DRILL_REPLACE", "CLICK", 0, "1", "Category",
                    config = """{"paramName":"category"}"""),
                ActionExportConfig("Lost source", "FILTER", "CLICK", 9, "1", "Category")
            )
        )
        val now = Instant.now()
        val created = widgets.mapIndexed { i, w ->
            WidgetResponse(id = 500L + i, widgetType = w.widgetType, title = w.title, body = null, queryId = null,
                datasourceId = 3, rawSql = w.rawSql, chartConfig = w.chartConfig, position = w.position, style = w.style,
                paramMapping = w.paramMapping, sortOrder = w.sortOrder, isVisible = true, createdAt = now, updatedAt = now)
        }
        whenever(reportService.createReport(any(), any())).thenReturn(ReportResponse(
            id = 70, slug = "s", name = "Sales", description = null, reportType = ReportType.STANDARD, layout = "{}",
            settings = "{}", status = ReportStatus.DRAFT, isTemplate = false, thumbnailUrl = null, folderId = null,
            parameters = emptyList(), widgets = created, createdBy = 1, updatedBy = 1, createdAt = now, updatedAt = now))

        templateService.importReport(ImportReportRequest(config = config, datasourceId = 3), userId = 1)

        val actions = argumentCaptor<DashboardActionRequest>()
        verify(interactiveService, times(1)).createAction(actions.capture())
        with(actions.firstValue) {
            assertEquals(70L, reportId)
            assertEquals(500L, sourceWidgetId)
            assertEquals("501", targetWidgetIds)
            assertEquals("category", this.config["paramName"])
        }
        val rule = argumentCaptor<VisibilityRuleRequest>()
        verify(interactiveService).createVisibilityRule(rule.capture())
        assertEquals(501L, rule.firstValue.widgetId)
        val update = argumentCaptor<UpdateWidgetRequest>()
        verify(reportService).updateWidget(eq(502L), update.capture(), eq(1L))
        val button = mapper.readTree(update.firstValue.chartConfig)
        assertNull(button["toggleWidgetSortOrders"])
        assertEquals(listOf(501L), button["toggleWidgetIds"].map { it.asLong() })
    }
}
