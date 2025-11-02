package org.roto.widget

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.roto.data.RotoData
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.roto.MainActivity
import org.roto.data.MenuPreferencesDataSource
import org.roto.data.MenuRepository
import org.roto.data.MenuSelection
import org.roto.domain.DayResult
import org.roto.domain.getMenuForDate

class RotoTodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetState = loadWidgetState(context)
        val activeFocus = readStoredFocus(context, id) ?: widgetState.defaultFocus()
        val displaySummary = widgetState.summaryForFocus(activeFocus)
        writeStoredFocus(context, id, activeFocus)
        provideContent {
            RotoWidgetContent(
                state = widgetState,
                focus = activeFocus,
                summary = displaySummary,
                modifier = GlanceModifier
                    .padding(16.dp)
                    .wrapContentHeight()
                    .fillMaxWidth()
                    .padding(4.dp)
            )
        }
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(RotoTodayWidget::class.java)
            if (ids.isEmpty()) return
            ids.forEach { glanceId ->
                RotoTodayWidget().update(context, glanceId)
            }
        }
    }
}

class RotoTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RotoTodayWidget()
}

private suspend fun loadWidgetState(context: Context): WidgetState =
    withContext(Dispatchers.IO) {
        val preferences = MenuPreferencesDataSource(context)
        val repository = MenuRepository(context)
        val selection: MenuSelection? = runCatching {
            preferences.menuSelectionFlow.firstOrNull()
        }.getOrNull()

        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val rotaResult = repository.loadMenu(selection?.uriString?.let(Uri::parse))

        rotaResult.fold(
            onSuccess = { loadResult ->
                val data = loadResult.data
                val todayResult = getMenuForDate(data, today)
                val tomorrowResult = getMenuForDate(data, tomorrow)
                if (todayResult == null && tomorrowResult == null) {
                    val nextAvailable = findNextAvailableDay(data, today)
                    WidgetState(
                        rotaName = data.rotaName.ifBlank { "Roto" },
                        today = null,
                        tomorrow = null,
                        fallbackMessage = nextAvailable?.let {
                            "No rota entries for today. Next rota: ${it.dateLabel}"
                        } ?: "No rota entries available."
                    )
                } else {
                    WidgetState(
                        rotaName = data.rotaName.ifBlank { "Roto" },
                        today = todayResult?.toSummary("Today", DayFocus.TODAY),
                        tomorrow = tomorrowResult?.toSummary("Tomorrow", DayFocus.TOMORROW),
                        fallbackMessage = null
                    )
                }
            },
            onFailure = { error ->
                WidgetState(
                    rotaName = "Roto",
                    today = null,
                    tomorrow = null,
                    fallbackMessage = error.message ?: "Add a rota in the app to populate the widget."
                )
            }
        )
    }

private data class WidgetState(
    val rotaName: String,
    val today: DaySummary?,
    val tomorrow: DaySummary?,
    val fallbackMessage: String?
)

private data class DaySummary(
    val title: String,
    val dateLabel: String,
    val lines: List<String>,
    val isClosed: Boolean,
    val closedReason: String?,
    val focus: DayFocus
)

@Composable
private fun RotoWidgetContent(
    state: WidgetState,
    focus: DayFocus,
    summary: DaySummary?,
    modifier: GlanceModifier = GlanceModifier
) {
    val openAppAction = actionStartActivity<MainActivity>()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(12.dp)
            .cornerRadius(16.dp)
    ) {
        Text(
            text = state.rotaName,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TitleColor)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        ToggleRow(
            activeFocus = focus,
            todayAvailable = state.today != null,
            tomorrowAvailable = state.tomorrow != null,
            openAppAction = openAppAction
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (summary != null) {
            val background = if (focus == DayFocus.TODAY) TodayBackground else TomorrowBackground
            DaySection(
                summary = summary,
                background = background
            )
        } else {
            val message = state.fallbackMessage
                ?: "No rota recorded for ${focus.displayLabel().lowercase()} yet."
            Text(
                text = message,
                style = TextStyle(fontSize = 12.sp, color = SecondaryTextColor)
            )
        }
    }
}

@Composable
private fun DaySection(
    summary: DaySummary,
    background: ColorProvider
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(background)
            .cornerRadius(12.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = summary.title,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryTextColor)
        )
        Text(
            text = summary.dateLabel,
            style = TextStyle(fontSize = 12.sp, color = SecondaryTextColor)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        if (summary.isClosed) {
            Text(
                text = summary.closedReason?.takeIf { it.isNotBlank() } ?: "Closed day",
                style = TextStyle(fontSize = 12.sp, color = PrimaryTextColor)
            )
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(116.dp)
            ) {
                if (summary.lines.isEmpty()) {
                    item {
                        Text(
                            text = "No entries recorded.",
                            style = TextStyle(fontSize = 12.sp, color = PrimaryTextColor)
                        )
                    }
                } else {
                    items(summary.lines) { line ->
                        Text(
                            text = line,
                            style = TextStyle(fontSize = 12.sp, color = PrimaryTextColor)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenAppLink(action: Action) {
    Text(
        text = "Open app",
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = PrimaryTextColor),
        modifier = GlanceModifier
            .background(ChipUnselectedBackground)
            .cornerRadius(8.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable(action)
    )
}

private fun DayResult.toSummary(title: String, focus: DayFocus): DaySummary {
    val slotLines = slots.map { slot ->
        "${slot.label}: ${slot.text}"
    }
    val noteLines = notes.map { "• $it" }
    val lines = slotLines + noteLines
    return DaySummary(
        title = title,
        dateLabel = formattedDate,
        lines = lines,
        isClosed = isClosed,
        closedReason = closedReason,
        focus = focus
    )
}

private fun findNextAvailableDay(data: RotoData, startDate: LocalDate): DaySummary? {
    for (offset in 1..7) {
        val candidateDate = startDate.plusDays(offset.toLong())
        val candidate = getMenuForDate(data, candidateDate)
        if (candidate != null) {
            return candidate.toSummary("Next rota", DayFocus.TODAY)
        }
    }
    return null
}

private val BackgroundColor = ColorProvider(color = Color(0xFFF4FBFA))

private val TitleColor = ColorProvider(color = Color(0xFF132327))

private val PrimaryTextColor = ColorProvider(color = Color(0xFF132327))

private val SecondaryTextColor = ColorProvider(color = Color(0xFF2B4548))

private val TodayBackground = ColorProvider(color = Color(0xFFD7F2EB))

private val TomorrowBackground = ColorProvider(color = Color(0xFFFFF3D6))

private val ChipSelectedBackground = ColorProvider(color = Color(0xFF00BF93))
private val ChipUnselectedBackground = ColorProvider(color = Color(0xFFE0EEEB))
private val ChipSelectedText = ColorProvider(color = Color(0xFF00382B))
private val ChipUnselectedText = ColorProvider(color = Color(0xFF2B4548))

private enum class DayFocus {
    TODAY, TOMORROW;

    fun other(): DayFocus = if (this == TODAY) TOMORROW else TODAY
    fun displayLabel(): String = if (this == TODAY) "Tomorrow" else "Today"
}

@Composable
private fun ToggleRow(
    activeFocus: DayFocus,
    todayAvailable: Boolean,
    tomorrowAvailable: Boolean,
    openAppAction: Action
) {
    val todayAction = if (todayAvailable) actionRunCallback<ShowTodayCallback>() else null
    val tomorrowAction = if (tomorrowAvailable) actionRunCallback<ShowTomorrowCallback>() else null
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        ToggleChip(
            label = "Today",
            isSelected = activeFocus == DayFocus.TODAY,
            modifier = GlanceModifier
                .padding(end = 4.dp),
            onClick = todayAction
        )
        ToggleChip(
            label = "Tomorrow",
            isSelected = activeFocus == DayFocus.TOMORROW,
            modifier = GlanceModifier
                .padding(end = 8.dp),
            onClick = tomorrowAction
        )
        OpenAppLink(openAppAction)
    }
}

@Composable
private fun ToggleChip(
    label: String,
    isSelected: Boolean,
    modifier: GlanceModifier,
    onClick: Action?
) {
    val baseModifier = modifier
        .background(if (isSelected) ChipSelectedBackground else ChipUnselectedBackground)
        .cornerRadius(12.dp)
        .padding(horizontal = 8.dp, vertical = 6.dp)
    val clickableModifier = if (onClick != null) baseModifier.clickable(onClick) else baseModifier
    Column(modifier = clickableModifier) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) ChipSelectedText else ChipUnselectedText
            )
        )
    }
}

private const val WIDGET_PREFS_NAME = "roto_widget_prefs"
private const val FOCUS_KEY_PREFIX = "focus_"

private fun readStoredFocus(context: Context, glanceId: GlanceId): DayFocus? {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    val value = context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        .getString("$FOCUS_KEY_PREFIX$appWidgetId", null)
    return value?.let { runCatching { DayFocus.valueOf(it) }.getOrNull() }
}

private fun writeStoredFocus(context: Context, glanceId: GlanceId, focus: DayFocus) {
    val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
    context.getSharedPreferences(WIDGET_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString("$FOCUS_KEY_PREFIX$appWidgetId", focus.name)
        .apply()
}

private fun WidgetState.summaryForFocus(focus: DayFocus): DaySummary? =
    when (focus) {
        DayFocus.TODAY -> today
        DayFocus.TOMORROW -> tomorrow
    }

private fun WidgetState.defaultFocus(): DayFocus =
    when {
        today != null -> DayFocus.TODAY
        tomorrow != null -> DayFocus.TOMORROW
        else -> DayFocus.TODAY
    }

private class ShowTodayCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        writeStoredFocus(context, glanceId, DayFocus.TODAY)
        RotoTodayWidget().update(context, glanceId)
    }
}

private class ShowTomorrowCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        writeStoredFocus(context, glanceId, DayFocus.TOMORROW)
        RotoTodayWidget().update(context, glanceId)
    }
}
