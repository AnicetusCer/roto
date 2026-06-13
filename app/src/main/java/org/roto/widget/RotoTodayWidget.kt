package org.roto.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.text.TextAlign
import androidx.glance.unit.ColorProvider
import android.content.res.Configuration
import org.roto.data.RotoData
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.roto.MainActivity
import org.roto.data.MenuPreferencesDataSource
import org.roto.data.MenuRepository
import org.roto.data.MenuSelection
import org.roto.data.MenuSelectionType
import org.roto.data.ThemeMode
import org.roto.data.ThemeOption
import org.roto.data.ThemePreferencesDataSource
import org.roto.domain.DayResult
import org.roto.domain.getMenuForDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.glance.state.PreferencesGlanceStateDefinition

class RotoTodayWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(150.dp, 120.dp),
            DpSize(180.dp, 200.dp),
            DpSize(220.dp, 280.dp)
        )
    )

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
        WidgetPresentationStore.update(id, WidgetPresentation(widgetState, activeFocus))
        Log.d(
            TAG,
            "provideGlance: today=${widgetState.today != null}, tomorrow=${widgetState.tomorrow != null}, focus=$activeFocus"
        )
        provideContent {
            val presentationFlow = remember(id) { WidgetPresentationStore.flowFor(id) }
            val presentation by presentationFlow.collectAsState()
            RotoWidgetContent(
                state = presentation.state,
                focus = presentation.focus,
                summary = presentation.state.summaryForFocus(presentation.focus),
                modifier = GlanceModifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        WidgetPresentationStore.clear(glanceId)
        super.onDelete(context, glanceId)
    }

    companion object {
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(RotoTodayWidget::class.java)
            if (ids.isEmpty()) return
            for (glanceId in ids) {
                refreshSingle(context, glanceId, fetchRemote = false)
            }
            WidgetRefreshScheduler.scheduleDailyRefresh(context)
        }

        suspend fun refreshSingle(context: Context, glanceId: GlanceId, fetchRemote: Boolean = false) {
            val freshState = computeWidgetState(context, fetchRemote)
            val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
            val storedFocus = prefs[FOCUS_KEY]?.let { runCatching { DayFocus.valueOf(it) }.getOrNull() }
            val activeFocus = storedFocus ?: freshState.defaultFocus()

            WidgetPresentationStore.update(glanceId, WidgetPresentation(freshState, activeFocus))
            updateAppWidgetState(context, glanceId) { mutablePrefs ->
                mutablePrefs[STATE_CACHE_KEY] = cacheJson.encodeToString(freshState.toCached())
                mutablePrefs[CACHE_DIRTY_KEY] = false
            }
            RotoTodayWidget().update(context, glanceId)
        }
    }
}

class RotoTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RotoTodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetRefreshScheduler.scheduleDailyRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WidgetRefreshScheduler.cancel(context)
    }
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

    val freshState = computeWidgetState(context, fetchRemote = false)

    updateAppWidgetState(context, glanceId) { mutablePrefs ->
        mutablePrefs[STATE_CACHE_KEY] = cacheJson.encodeToString(freshState.toCached())
        mutablePrefs[CACHE_DIRTY_KEY] = false
    }
    return freshState
}

private suspend fun computeWidgetState(context: Context, fetchRemote: Boolean = false): WidgetState =
    withContext(Dispatchers.IO) {
        val preferences = MenuPreferencesDataSource(context)
        val themePreferences = ThemePreferencesDataSource(context)
        val repository = MenuRepository(context)
        val selection: MenuSelection? = runCatching {
            preferences.menuSelectionFlow.firstOrNull()
        }.getOrNull()
        val themeOption: ThemeOption = runCatching {
            themePreferences.themeOptionFlow.firstOrNull()
        }.getOrNull() ?: ThemeOption.SYSTEM

        val today = LocalDate.now()
        val tomorrow = today.plusDays(1)
        val systemDark = context.isSystemInDarkMode()
        val themeMode: ThemeMode = runCatching {
            themePreferences.themeModeFlow.firstOrNull()
        }.getOrNull() ?: ThemeMode.SYSTEM
        val resolvedDark = when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
        val rotaResult = repository.loadMenu(
            selection = selection,
            allowDownloadsFallback = false,
            fetchRemote = fetchRemote && selection?.type == MenuSelectionType.REMOTE_LINK
        )

        rotaResult.fold(
            onSuccess = { loadResult ->
                val data = loadResult.data
                val todayResult = getMenuForDate(data, today)
                val tomorrowResult = getMenuForDate(data, tomorrow)
                if (todayResult == null && tomorrowResult == null) {
                    val nextAvailable = findNextAvailableDay(data, today)
                    Log.d(TAG, "computeWidgetState: no entries today/tomorrow; next=${nextAvailable?.dateLabel}")
                    WidgetState(
                        rotaName = data.rotaName.ifBlank { "Roto" },
                        today = null,
                        tomorrow = null,
                        fallbackMessage = nextAvailable?.let {
                            "No rota entries for today. Next rota: ${it.dateLabel}"
                        } ?: "No rota entries available."
                        ,
                        themeOption = themeOption,
                        isDark = resolvedDark,
                        canFetchRemote = selection?.type == MenuSelectionType.REMOTE_LINK
                    )
                } else {
                    Log.d(TAG, "computeWidgetState: todayPresent=${todayResult != null}, tomorrowPresent=${tomorrowResult != null}")
                    WidgetState(
                        rotaName = data.rotaName.ifBlank { "Roto" },
                        today = todayResult?.toSummary("Today", DayFocus.TODAY),
                        tomorrow = tomorrowResult?.toSummary("Tomorrow", DayFocus.TOMORROW),
                        fallbackMessage = null,
                        themeOption = themeOption,
                        isDark = resolvedDark,
                        canFetchRemote = selection?.type == MenuSelectionType.REMOTE_LINK
                    )
                }
            },
            onFailure = { error ->
                WidgetState(
                    rotaName = "Roto",
                    today = null,
                    tomorrow = null,
                    fallbackMessage = error.message ?: "Add a rota in the app to populate the widget.",
                    themeOption = themeOption,
                    isDark = resolvedDark,
                    canFetchRemote = selection?.type == MenuSelectionType.REMOTE_LINK
                )
            }
        )
    }

private data class WidgetState(
    val rotaName: String,
    val today: DaySummary?,
    val tomorrow: DaySummary?,
    val fallbackMessage: String?,
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val isDark: Boolean = false,
    val canFetchRemote: Boolean = false
)

private data class DaySummary(
    val title: String,
    val dateLabel: String,
    val specialEvents: List<String> = emptyList(),
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
    val fallbackMessage: String?,
    val themeOption: ThemeOption = ThemeOption.SYSTEM,
    val isDark: Boolean = false,
    val canFetchRemote: Boolean = false
)

@Serializable
private data class CachedDaySummary(
    val title: String,
    val dateLabel: String,
    val specialEvents: List<String> = emptyList(),
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
        fallbackMessage = fallbackMessage,
        themeOption = themeOption,
        isDark = isDark,
        canFetchRemote = canFetchRemote
    )

private fun CachedDaySummary.toDaySummary(): DaySummary =
    DaySummary(
        title = title,
        dateLabel = dateLabel,
        specialEvents = specialEvents,
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
        fallbackMessage = fallbackMessage,
        themeOption = themeOption,
        isDark = isDark,
        canFetchRemote = canFetchRemote
    )

private fun DaySummary.toCached(): CachedDaySummary =
    CachedDaySummary(
        title = title,
        dateLabel = dateLabel,
        specialEvents = specialEvents,
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
    val palette = paletteForTheme(state.themeOption, state.isDark)
    val openAppAction = actionStartActivity<MainActivity>()
    val reloadAction = actionRunCallback<ReloadWidgetCallback>()
    val fetchAction = actionRunCallback<FetchRemoteWidgetCallback>()
    Column(
        modifier = modifier
            .background(palette.background)
            .cornerRadius(10.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = state.rotaName,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = palette.titleColor)
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        ToggleRow(activeFocus = focus, palette = palette)
        Spacer(modifier = GlanceModifier.height(6.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Horizontal.End
        ) {
            OpenAppLink(openAppAction, palette)
            Spacer(modifier = GlanceModifier.width(4.dp))
            ActionChip(
                label = "Reload",
                modifier = GlanceModifier,
                onClick = reloadAction,
                palette = palette
            )
            if (state.canFetchRemote) {
                Spacer(modifier = GlanceModifier.width(4.dp))
                ActionChip(
                    label = "Fetch",
                    modifier = GlanceModifier,
                    onClick = fetchAction,
                    palette = palette
                )
            }
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        if (summary != null) {
            val background = if (focus == DayFocus.TODAY) palette.todayBackground else palette.tomorrowBackground
            DaySection(
                summary = summary,
                palette = palette,
                background = background,
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxWidth()
            )
        } else {
            val message = state.fallbackMessage
                ?: "There is no rota data for ${focus.displayLabel().lowercase()}."
            Text(
                text = message,
                style = TextStyle(fontSize = 12.sp, color = palette.secondaryTextColor)
            )
        }
    }
}

@Composable
private fun DaySection(
    summary: DaySummary,
    palette: WidgetPalette,
    background: ColorProvider,
    modifier: GlanceModifier = GlanceModifier
) {
    Column(
        modifier = modifier
            .background(background)
            .cornerRadius(8.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = summary.title,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = palette.primaryTextColor)
        )
        Text(
            text = summary.dateLabel,
            style = TextStyle(fontSize = 12.sp, color = palette.secondaryTextColor)
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        summary.specialEvents.takeIf { it.isNotEmpty() }?.forEachIndexed { index, event ->
            SpecialEventBadge(text = event, palette = palette)
            if (index != summary.specialEvents.lastIndex) {
                Spacer(modifier = GlanceModifier.height(6.dp))
            }
        }
        if (summary.isClosed) {
            Text(
                text = summary.closedReason?.takeIf { it.isNotBlank() } ?: "Closed day",
                style = TextStyle(fontSize = 12.sp, color = palette.primaryTextColor)
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
        } else {
            LazyColumn(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
            ) {
                if (summary.lines.isEmpty()) {
                    item {
                        Text(
                            text = "No entries recorded.",
                            style = TextStyle(fontSize = 12.sp, color = palette.primaryTextColor)
                        )
                    }
                } else {
                    items(summary.lines) { line ->
                        val parts = line.split(": ", limit = 2)
                        if (parts.size == 2) {
                            Row {
                                Text(
                                    text = parts[0] + ":",
                                    style = TextStyle(
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = palette.primaryTextColor
                                    )
                                )
                                Text(
                                    text = " " + parts[1],
                                    style = TextStyle(fontSize = 12.sp, color = palette.primaryTextColor)
                                )
                            }
                        } else {
                            Text(
                                text = line,
                                style = TextStyle(fontSize = 12.sp, color = palette.primaryTextColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecialEventBadge(
    text: String,
    palette: WidgetPalette
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(palette.specialBadgeBackground)
            .cornerRadius(8.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, color = palette.primaryTextColor)
        )
    }
}

@Composable
private fun OpenAppLink(action: Action, palette: WidgetPalette) {
    Text(
        text = "Open",
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, color = palette.primaryTextColor),
        modifier = GlanceModifier
            .background(palette.chipUnselectedBackground)
            .cornerRadius(8.dp)
            .padding(horizontal = 6.dp, vertical = 4.dp)
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
        specialEvents = specialEvents,
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

private data class WidgetPalette(
    val background: ColorProvider,
    val titleColor: ColorProvider,
    val primaryTextColor: ColorProvider,
    val secondaryTextColor: ColorProvider,
    val todayBackground: ColorProvider,
    val tomorrowBackground: ColorProvider,
    val chipSelectedBackground: ColorProvider,
    val chipUnselectedBackground: ColorProvider,
    val chipSelectedText: ColorProvider,
    val chipUnselectedText: ColorProvider,
    val specialBadgeBackground: ColorProvider
)

private fun paletteForTheme(option: ThemeOption, isDark: Boolean): WidgetPalette =
    when (option) {
        ThemeOption.SYSTEM -> if (isDark) defaultDarkPalette else defaultLightPalette
        ThemeOption.LIGHT -> defaultLightPalette
        ThemeOption.DARK -> defaultDarkPalette
        ThemeOption.FOREST -> if (isDark) forestDarkPalette else forestLightPalette
        ThemeOption.SUNSET -> if (isDark) sunsetDarkPalette else sunsetLightPalette
        ThemeOption.OCEAN -> if (isDark) oceanDarkPalette else oceanLightPalette
        ThemeOption.BLOSSOM -> if (isDark) blossomDarkPalette else blossomLightPalette
        ThemeOption.MIDNIGHT -> if (isDark) midnightDarkPalette else midnightLightPalette
        ThemeOption.SAND -> if (isDark) sandDarkPalette else sandLightPalette
    }

private val defaultLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFF4FBFA)),
    titleColor = ColorProvider(Color(0xFF132327)),
    primaryTextColor = ColorProvider(Color(0xFF132327)),
    secondaryTextColor = ColorProvider(Color(0xFF2B4548)),
    todayBackground = ColorProvider(Color(0xFFD7F2EB)),
    tomorrowBackground = ColorProvider(Color(0xFFFFF3D6)),
    chipSelectedBackground = ColorProvider(Color(0xFF00BF93)),
    chipUnselectedBackground = ColorProvider(Color(0xFFE0EEEB)),
    chipSelectedText = ColorProvider(Color(0xFF00382B)),
    chipUnselectedText = ColorProvider(Color(0xFF2B4548)),
    specialBadgeBackground = ColorProvider(Color(0xFFEAF3FF))
)

private val defaultDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF0E1A1D)),
    titleColor = ColorProvider(Color(0xFFE3F0EE)),
    primaryTextColor = ColorProvider(Color(0xFFE3F0EE)),
    secondaryTextColor = ColorProvider(Color(0xFFC2D7D6)),
    todayBackground = ColorProvider(Color(0xFF234244)),
    tomorrowBackground = ColorProvider(Color(0xFF1B2F33)),
    chipSelectedBackground = ColorProvider(Color(0xFF66E6C7)),
    chipUnselectedBackground = ColorProvider(Color(0xFF234244)),
    chipSelectedText = ColorProvider(Color(0xFF00241C)),
    chipUnselectedText = ColorProvider(Color(0xFFC2D7D6)),
    specialBadgeBackground = ColorProvider(Color(0xFF1B2F33))
)

private val forestLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFF1F6F2)),
    titleColor = ColorProvider(Color(0xFF1E3326)),
    primaryTextColor = ColorProvider(Color(0xFF1E3326)),
    secondaryTextColor = ColorProvider(Color(0xFF2E4A35)),
    todayBackground = ColorProvider(Color(0xFFDCE7DD)),
    tomorrowBackground = ColorProvider(Color(0xFFEDF3EC)),
    chipSelectedBackground = ColorProvider(Color(0xFF2F7D4B)),
    chipUnselectedBackground = ColorProvider(Color(0xFFDCE7DD)),
    chipSelectedText = ColorProvider(Color(0xFFFFFFFF)),
    chipUnselectedText = ColorProvider(Color(0xFF2E4A35)),
    specialBadgeBackground = ColorProvider(Color(0xFFE3F0E9))
)

private val forestDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF0E1912)),
    titleColor = ColorProvider(Color(0xFFE2F0E6)),
    primaryTextColor = ColorProvider(Color(0xFFE2F0E6)),
    secondaryTextColor = ColorProvider(Color(0xFFCBE3D2)),
    todayBackground = ColorProvider(Color(0xFF2B4733)),
    tomorrowBackground = ColorProvider(Color(0xFF1A2B1E)),
    chipSelectedBackground = ColorProvider(Color(0xFF7FD1A2)),
    chipUnselectedBackground = ColorProvider(Color(0xFF2B4733)),
    chipSelectedText = ColorProvider(Color(0xFF00321C)),
    chipUnselectedText = ColorProvider(Color(0xFFCBE3D2)),
    specialBadgeBackground = ColorProvider(Color(0xFF1E3324))
)

private val sunsetLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFFFF6F2)),
    titleColor = ColorProvider(Color(0xFF2E1B16)),
    primaryTextColor = ColorProvider(Color(0xFF2E1B16)),
    secondaryTextColor = ColorProvider(Color(0xFF5A3E35)),
    todayBackground = ColorProvider(Color(0xFFF2DFD7)),
    tomorrowBackground = ColorProvider(Color(0xFFFFE8D9)),
    chipSelectedBackground = ColorProvider(Color(0xFFCE4D53)),
    chipUnselectedBackground = ColorProvider(Color(0xFFF2DFD7)),
    chipSelectedText = ColorProvider(Color(0xFFFFFFFF)),
    chipUnselectedText = ColorProvider(Color(0xFF5A3E35)),
    specialBadgeBackground = ColorProvider(Color(0xFFF8E9DF))
)

private val sunsetDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF1B1210)),
    titleColor = ColorProvider(Color(0xFFF6E8E3)),
    primaryTextColor = ColorProvider(Color(0xFFF6E8E3)),
    secondaryTextColor = ColorProvider(Color(0xFFE3CFC8)),
    todayBackground = ColorProvider(Color(0xFF3C2B27)),
    tomorrowBackground = ColorProvider(Color(0xFF2A1D19)),
    chipSelectedBackground = ColorProvider(Color(0xFFFFA6A9)),
    chipUnselectedBackground = ColorProvider(Color(0xFF3C2B27)),
    chipSelectedText = ColorProvider(Color(0xFF3D0209)),
    chipUnselectedText = ColorProvider(Color(0xFFE3CFC8)),
    specialBadgeBackground = ColorProvider(Color(0xFF2A1D19))
)

private val oceanLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFF2F8FC)),
    titleColor = ColorProvider(Color(0xFF0D2430)),
    primaryTextColor = ColorProvider(Color(0xFF0D2430)),
    secondaryTextColor = ColorProvider(Color(0xFF274556)),
    todayBackground = ColorProvider(Color(0xFFD7E8F3)),
    tomorrowBackground = ColorProvider(Color(0xFFE5F3FC)),
    chipSelectedBackground = ColorProvider(Color(0xFF0E88C8)),
    chipUnselectedBackground = ColorProvider(Color(0xFFD7E8F3)),
    chipSelectedText = ColorProvider(Color(0xFFFFFFFF)),
    chipUnselectedText = ColorProvider(Color(0xFF274556)),
    specialBadgeBackground = ColorProvider(Color(0xFFE5F3FC))
)

private val oceanDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF0A1620)),
    titleColor = ColorProvider(Color(0xFFE3EFF7)),
    primaryTextColor = ColorProvider(Color(0xFFE3EFF7)),
    secondaryTextColor = ColorProvider(Color(0xFFC7D8E4)),
    todayBackground = ColorProvider(Color(0xFF1E3342)),
    tomorrowBackground = ColorProvider(Color(0xFF132534)),
    chipSelectedBackground = ColorProvider(Color(0xFF6BCBFF)),
    chipUnselectedBackground = ColorProvider(Color(0xFF1E3342)),
    chipSelectedText = ColorProvider(Color(0xFF00263A)),
    chipUnselectedText = ColorProvider(Color(0xFFC7D8E4)),
    specialBadgeBackground = ColorProvider(Color(0xFF132534))
)

private val blossomLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFFDF6F8)),
    titleColor = ColorProvider(Color(0xFF2F1921)),
    primaryTextColor = ColorProvider(Color(0xFF2F1921)),
    secondaryTextColor = ColorProvider(Color(0xFF503741)),
    todayBackground = ColorProvider(Color(0xFFEED9E1)),
    tomorrowBackground = ColorProvider(Color(0xFFF7E6ED)),
    chipSelectedBackground = ColorProvider(Color(0xFFD65C7A)),
    chipUnselectedBackground = ColorProvider(Color(0xFFEED9E1)),
    chipSelectedText = ColorProvider(Color(0xFFFFFFFF)),
    chipUnselectedText = ColorProvider(Color(0xFF503741)),
    specialBadgeBackground = ColorProvider(Color(0xFFF7E6ED))
)

private val blossomDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF1A1116)),
    titleColor = ColorProvider(Color(0xFFF4E8ED)),
    primaryTextColor = ColorProvider(Color(0xFFF4E8ED)),
    secondaryTextColor = ColorProvider(Color(0xFFE2CED7)),
    todayBackground = ColorProvider(Color(0xFF3A2A32)),
    tomorrowBackground = ColorProvider(Color(0xFF291C23)),
    chipSelectedBackground = ColorProvider(Color(0xFFF29CB7)),
    chipUnselectedBackground = ColorProvider(Color(0xFF3A2A32)),
    chipSelectedText = ColorProvider(Color(0xFF430C24)),
    chipUnselectedText = ColorProvider(Color(0xFFE2CED7)),
    specialBadgeBackground = ColorProvider(Color(0xFF291C23))
)

private val midnightLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFF3F4FB)),
    titleColor = ColorProvider(Color(0xFF111424)),
    primaryTextColor = ColorProvider(Color(0xFF111424)),
    secondaryTextColor = ColorProvider(Color(0xFF2C304A)),
    todayBackground = ColorProvider(Color(0xFFDDE0F1)),
    tomorrowBackground = ColorProvider(Color(0xFFEBEDFA)),
    chipSelectedBackground = ColorProvider(Color(0xFF3A4E8C)),
    chipUnselectedBackground = ColorProvider(Color(0xFFDDE0F1)),
    chipSelectedText = ColorProvider(Color(0xFFFFFFFF)),
    chipUnselectedText = ColorProvider(Color(0xFF2C304A)),
    specialBadgeBackground = ColorProvider(Color(0xFFEBEDFA))
)

private val midnightDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF0A0D18)),
    titleColor = ColorProvider(Color(0xFFE6EAFF)),
    primaryTextColor = ColorProvider(Color(0xFFE6EAFF)),
    secondaryTextColor = ColorProvider(Color(0xFFC7CCE5)),
    todayBackground = ColorProvider(Color(0xFF1F2539)),
    tomorrowBackground = ColorProvider(Color(0xFF13192B)),
    chipSelectedBackground = ColorProvider(Color(0xFF8FA4FF)),
    chipUnselectedBackground = ColorProvider(Color(0xFF1F2539)),
    chipSelectedText = ColorProvider(Color(0xFF0A1028)),
    chipUnselectedText = ColorProvider(Color(0xFFC7CCE5)),
    specialBadgeBackground = ColorProvider(Color(0xFF13192B))
)

private val sandLightPalette = WidgetPalette(
    background = ColorProvider(Color(0xFFFBF5EB)),
    titleColor = ColorProvider(Color(0xFF2A1E10)),
    primaryTextColor = ColorProvider(Color(0xFF2A1E10)),
    secondaryTextColor = ColorProvider(Color(0xFF4F412F)),
    todayBackground = ColorProvider(Color(0xFFE9DDCD)),
    tomorrowBackground = ColorProvider(Color(0xFFF5EADA)),
    chipSelectedBackground = ColorProvider(Color(0xFFC48A3A)),
    chipUnselectedBackground = ColorProvider(Color(0xFFE9DDCD)),
    chipSelectedText = ColorProvider(Color(0xFFFFFFFF)),
    chipUnselectedText = ColorProvider(Color(0xFF4F412F)),
    specialBadgeBackground = ColorProvider(Color(0xFFF5EADA))
)

private val sandDarkPalette = WidgetPalette(
    background = ColorProvider(Color(0xFF171008)),
    titleColor = ColorProvider(Color(0xFFEFE2CF)),
    primaryTextColor = ColorProvider(Color(0xFFEFE2CF)),
    secondaryTextColor = ColorProvider(Color(0xFFE0CDB4)),
    todayBackground = ColorProvider(Color(0xFF3A2D1F)),
    tomorrowBackground = ColorProvider(Color(0xFF251A10)),
    chipSelectedBackground = ColorProvider(Color(0xFFF0C27D)),
    chipUnselectedBackground = ColorProvider(Color(0xFF3A2D1F)),
    chipSelectedText = ColorProvider(Color(0xFF3A2700)),
    chipUnselectedText = ColorProvider(Color(0xFFE0CDB4)),
    specialBadgeBackground = ColorProvider(Color(0xFF251A10))
)

private fun Context.isSystemInDarkMode(): Boolean =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

@Serializable
private enum class DayFocus {
    TODAY, TOMORROW;

    fun other(): DayFocus = if (this == TODAY) TOMORROW else TODAY
    fun displayLabel(): String = if (this == TODAY) "Tomorrow" else "Today"
}

@Composable
private fun ToggleRow(
    activeFocus: DayFocus,
    palette: WidgetPalette
) {
    val todayAction = actionRunCallback<ShowTodayCallback>()
    val tomorrowAction = actionRunCallback<ShowTomorrowCallback>()
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            ToggleChip(
                label = "Today",
                isSelected = activeFocus == DayFocus.TODAY,
                modifier = GlanceModifier.fillMaxWidth(),
                onClick = todayAction,
                palette = palette
            )
        }
        Column(modifier = GlanceModifier.defaultWeight()) {
            ToggleChip(
                label = "Tomorrow",
                isSelected = activeFocus == DayFocus.TOMORROW,
                modifier = GlanceModifier.fillMaxWidth(),
                onClick = tomorrowAction,
                palette = palette
            )
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    isSelected: Boolean,
    modifier: GlanceModifier,
    onClick: Action?,
    palette: WidgetPalette
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .background(if (isSelected) palette.chipSelectedBackground else palette.chipUnselectedBackground)
        .cornerRadius(12.dp)
        .padding(horizontal = 10.dp, vertical = 7.dp)
    val clickableModifier = if (onClick != null) baseModifier.clickable(onClick) else baseModifier
    Column(
        modifier = clickableModifier,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isSelected) palette.chipSelectedText else palette.chipUnselectedText,
                textAlign = TextAlign.Center
            )
        )
    }
}

@Composable
private fun ActionChip(
    label: String,
    modifier: GlanceModifier,
    onClick: Action,
    palette: WidgetPalette
) {
    Column(
        modifier = modifier
            .background(palette.chipUnselectedBackground)
            .cornerRadius(12.dp)
            .padding(horizontal = 7.dp, vertical = 6.dp)
            .clickable(onClick),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = palette.chipUnselectedText,
                textAlign = TextAlign.Center
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
    WidgetPresentationStore.updateFocus(glanceId, focus)
    Log.d(TAG, "updateStoredFocus: focus set to $focus for $glanceId")
}

private data class WidgetPresentation(
    val state: WidgetState = WidgetState(
        rotaName = "Roto",
        today = null,
        tomorrow = null,
        fallbackMessage = "Loading rota..."
    ),
    val focus: DayFocus = DayFocus.TODAY
)

private object WidgetPresentationStore {
    private val flows = ConcurrentHashMap<GlanceId, MutableStateFlow<WidgetPresentation>>()

    fun flowFor(glanceId: GlanceId): MutableStateFlow<WidgetPresentation> =
        flows.getOrPut(glanceId) { MutableStateFlow(WidgetPresentation()) }

    fun update(glanceId: GlanceId, presentation: WidgetPresentation) {
        flowFor(glanceId).value = presentation
    }

    fun updateFocus(glanceId: GlanceId, focus: DayFocus) {
        val flow = flowFor(glanceId)
        flow.value = flow.value.copy(focus = focus)
    }

    fun clear(glanceId: GlanceId) {
        flows.remove(glanceId)
    }
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
        // First repaint immediately with cached state/focus, then reload local data in the background.
        RotoTodayWidget().update(context, glanceId)
        RotoTodayWidget.refreshSingle(context, glanceId, fetchRemote = false)
    }
}

class ShowTomorrowCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d(TAG, "ShowTomorrowCallback.onAction for $glanceId")
        updateStoredFocus(context, glanceId, DayFocus.TOMORROW)
        RotoTodayWidget().update(context, glanceId)
        RotoTodayWidget.refreshSingle(context, glanceId, fetchRemote = false)
    }
}

class ReloadWidgetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d(TAG, "ReloadWidgetCallback.onAction for $glanceId")
        RotoTodayWidget.refreshSingle(context, glanceId, fetchRemote = false)
        WidgetRefreshScheduler.scheduleDailyRefresh(context)
    }
}

class FetchRemoteWidgetCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Log.d(TAG, "FetchRemoteWidgetCallback.onAction for $glanceId")
        RotoTodayWidget.refreshSingle(context, glanceId, fetchRemote = true)
        WidgetRefreshScheduler.scheduleDailyRefresh(context)
    }
}
