package org.roto.widget

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.glance.state.PreferencesGlanceStateDefinition

class RotoTodayWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetState = loadWidgetState(context, id)
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)
        val storedFocus = prefs[FOCUS_KEY]?.let { runCatching { DayFocus.valueOf(it) }.getOrNull() }
        val activeFocus = storedFocus ?: widgetState.defaultFocus()
        if (storedFocus != activeFocus) {
            updateAppWidgetState(context, id) { mutablePrefs ->
                mutablePrefs[FOCUS_KEY] = activeFocus.name
            }
        }
        val displaySummary = widgetState.summaryForFocus(activeFocus)
        Log.d(TAG, "provideGlance: today=${widgetState.today != null}, tomorrow=${widgetState.tomorrow != null}, focus=$activeFocus")
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
            for (glanceId in ids) {
                updateAppWidgetState(context, glanceId) { prefs ->
                    prefs[CACHE_DIRTY_KEY] = true
                }
            }
            for (glanceId in ids) {
                RotoTodayWidget().update(context, glanceId)
            }
        }
    }
}

class RotoTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RotoTodayWidget()
}

private suspend fun loadWidgetState(
    context: Context,
    glanceId: GlanceId
): WidgetState {
    val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
    val cachedState = prefs[STATE_CACHE_KEY]?.let { cachedJson ->
        runCatching { cacheJson.decodeFromString<CachedWidgetState>(cachedJson) }.getOrNull()
    }
    val cacheDirty = prefs[CACHE_DIRTY_KEY] ?: true
    if (!cacheDirty && cachedState != null) {
        Log.d(TAG, "loadWidgetState: using cached widget state")
        return cachedState.toWidgetState()
    }

    val freshState = withContext(Dispatchers.IO) {
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
                    Log.d(TAG, "loadWidgetState: no entries today/tomorrow; next=${nextAvailable?.dateLabel}")
                    WidgetState(
                        rotaName = data.rotaName.ifBlank { "Roto" },
                        today = null,
                        tomorrow = null,
                        fallbackMessage = nextAvailable?.let {
                            "No rota entries for today. Next rota: ${it.dateLabel}"
                        } ?: "No rota entries available."
                    )
                } else {
                    Log.d(TAG, "loadWidgetState: todayPresent=${todayResult != null}, tomorrowPresent=${tomorrowResult != null}")
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

    updateAppWidgetState(context, glanceId) { mutablePrefs ->
        mutablePrefs[STATE_CACHE_KEY] = cacheJson.encodeToString(freshState.toCached())
        mutablePrefs[CACHE_DIRTY_KEY] = false
    }
    return freshState
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

@Serializable
private data class CachedWidgetState(
    val rotaName: String,
    val today: CachedDaySummary?,
    val tomorrow: CachedDaySummary?,
    val fallbackMessage: String?
)

@Serializable
private data class CachedDaySummary(
    val title: String,
    val dateLabel: String,
    val lines: List<String>,
    val isClosed: Boolean,
    val closedReason: String?,
    val focus: DayFocus
)

private fun CachedWidgetState.toWidgetState(): WidgetState =
    WidgetState(
        rotaName = rotaName,
        today = today?.toDaySummary(),
        tomorrow = tomorrow?.toDaySummary(),
        fallbackMessage = fallbackMessage
    )

private fun CachedDaySummary.toDaySummary(): DaySummary =
    DaySummary(
        title = title,
        dateLabel = dateLabel,
        lines = lines,
        isClosed = isClosed,
        closedReason = closedReason,
        focus = focus
    )

private fun WidgetState.toCached(): CachedWidgetState =
    CachedWidgetState(
        rotaName = rotaName,
        today = today?.toCached(),
        tomorrow = tomorrow?.toCached(),
        fallbackMessage = fallbackMessage
    )

private fun DaySummary.toCached(): CachedDaySummary =
    CachedDaySummary(
        title = title,
        dateLabel = dateLabel,
        lines = lines,
        isClosed = isClosed,
        closedReason = closedReason,
        focus = focus
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
                ?: "There is no rota data for ${focus.displayLabel().lowercase()}."
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

@Serializable
private enum class DayFocus {
    TODAY, TOMORROW;

    fun other(): DayFocus = if (this == TODAY) TOMORROW else TODAY
    fun displayLabel(): String = if (this == TODAY) "Tomorrow" else "Today"
}

@Composable
private fun ToggleRow(
    activeFocus: DayFocus,
    openAppAction: Action
) {
    val todayAction = actionRunCallback<ShowTodayCallback>()
    val tomorrowAction = actionRunCallback<ShowTomorrowCallback>()
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

private const val TAG = "RotoWidget"
private val FOCUS_KEY = stringPreferencesKey("active_focus")
private val STATE_CACHE_KEY = stringPreferencesKey("widget_state_cache")
private val CACHE_DIRTY_KEY = booleanPreferencesKey("widget_cache_dirty")
private val cacheJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private suspend fun updateStoredFocus(context: Context, glanceId: GlanceId, focus: DayFocus) {
    updateAppWidgetState(context, glanceId) { prefs ->
        prefs[FOCUS_KEY] = focus.name
    }
    Log.d(TAG, "updateStoredFocus: focus set to $focus for $glanceId")
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

class ShowTodayCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d(TAG, "ShowTodayCallback.onAction for $glanceId")
        updateStoredFocus(context, glanceId, DayFocus.TODAY)
        RotoTodayWidget().update(context, glanceId)
    }
}

class ShowTomorrowCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d(TAG, "ShowTomorrowCallback.onAction for $glanceId")
        updateStoredFocus(context, glanceId, DayFocus.TOMORROW)
        RotoTodayWidget().update(context, glanceId)
    }
}
