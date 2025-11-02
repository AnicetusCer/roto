package org.roto.widget

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
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
        provideContent {
            RotoWidgetContent(
                state = widgetState,
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
                WidgetState(
                    rotaName = data.rotaName.ifBlank { "Roto" },
                    today = todayResult?.toSummary("Today"),
                    tomorrow = tomorrowResult?.toSummary("Tomorrow"),
                    fallbackMessage = if (todayResult == null && tomorrowResult == null) {
                        "No rota entries found for today or tomorrow."
                    } else {
                        null
                    }
                )
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
    val closedReason: String?
)

@Composable
private fun RotoWidgetContent(
    state: WidgetState,
    modifier: GlanceModifier = GlanceModifier
) {
    val openAppAction = actionStartActivity<MainActivity>()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BackgroundColor)
            .padding(12.dp)
            .cornerRadius(16.dp)
            .clickable(openAppAction)
    ) {
        Text(
            text = state.rotaName,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TitleColor)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        var renderedSection = false
        state.today?.let {
            DaySection(summary = it, background = TodayBackground)
            renderedSection = true
        }
        state.tomorrow?.let {
            if (renderedSection) {
                Spacer(modifier = GlanceModifier.height(8.dp))
            }
            DaySection(summary = it, background = TomorrowBackground)
            renderedSection = true
        }
        if (state.today == null && state.tomorrow == null) {
            state.fallbackMessage?.let { message ->
                Text(
                    text = message,
                    style = TextStyle(fontSize = 12.sp, color = SecondaryTextColor)
                )
            }
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
            summary.lines.forEach { line ->
                Text(
                    text = line,
                    style = TextStyle(fontSize = 12.sp, color = PrimaryTextColor)
                )
            }
            if (summary.lines.isEmpty()) {
                Text(
                    text = "No entries recorded.",
                    style = TextStyle(fontSize = 12.sp, color = PrimaryTextColor)
                )
            }
        }
    }
}

private fun DayResult.toSummary(title: String): DaySummary {
    val lines = slots.take(3).map { slot ->
        "${slot.label}: ${slot.text}"
    }
    return DaySummary(
        title = title,
        dateLabel = formattedDate,
        lines = lines,
        isClosed = isClosed,
        closedReason = closedReason
    )
}

private val BackgroundColor = ColorProvider(color = Color(0xFFF4FBFA))

private val TitleColor = ColorProvider(color = Color(0xFF132327))

private val PrimaryTextColor = ColorProvider(color = Color(0xFF132327))

private val SecondaryTextColor = ColorProvider(color = Color(0xFF2B4548))

private val TodayBackground = ColorProvider(color = Color(0xFFD7F2EB))

private val TomorrowBackground = ColorProvider(color = Color(0xFFFFF3D6))
