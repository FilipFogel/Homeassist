@file:OptIn(ExperimentalMaterial3Api::class)

package se.hemma.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.hemma.app.data.PriceSettings
import se.hemma.app.domain.Dashboard
import se.hemma.app.domain.Fmt
import se.hemma.app.domain.HourSlot
import se.hemma.app.domain.MachinePlan
import se.hemma.app.domain.Planner
import se.hemma.app.domain.PriceLevel
import se.hemma.app.domain.TimeRange
import se.hemma.app.ui.theme.LevelColors
import se.hemma.app.ui.theme.color
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Översikten visar bara det viktigaste: pris just nu, billigaste timmarna och
 * när varje maskin ska startas. Allt annat ligger under "Visa mer" / tryck på raden.
 */
@Composable
fun HomeScreen(
    state: UiState,
    onRefresh: () -> Unit,
    onOpenMachines: () -> Unit,
) {
    val dash = state.dashboard
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.loading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp))) }
        }
        state.error?.let { err ->
            item { ErrorCard(err, onRefresh) }
        }
        if (dash == null || dash.today.isEmpty()) {
            if (!state.loading) item { EmptyCard(onRefresh) }
            return@LazyColumn
        }
        item(key = "cheap") { CheapHoursCard(dash, state.settings) }
        item(key = "now") { NowCard(dash) }
        item(key = "machines") { MachinesCard(dash, onOpenMachines) }
        item(key = "footer") { Footer(state) }
    }
}

// ---------------------------------------------------------------------------
// Gemensamt: "Visa mer" och utfällbar del
// ---------------------------------------------------------------------------

@Composable
private fun Details(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Column(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun ExpandToggle(expanded: Boolean, onToggle: () -> Unit, moreLabel: String = "Visa mer") {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (expanded) "Visa mindre" else moreLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(2.dp))
        Chevron(expanded, MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Chevron(expanded: Boolean, tint: Color, modifier: Modifier = Modifier) {
    Icon(
        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "Dölj detaljer" else "Visa detaljer",
        tint = tint,
        modifier = modifier.size(20.dp),
    )
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

// ---------------------------------------------------------------------------
// Pris just nu
// ---------------------------------------------------------------------------

@Composable
private fun NowCard(dash: Dashboard) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val level = dash.level
    HemmaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Just nu",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dash.currentTotal?.let { Fmt.perKwh(it) } ?: "–",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp,
                )
            }
            LevelBadge(level)
        }
        val hint = when (level) {
            PriceLevel.CHEAP -> "👍 Bra läge att köra något nu"
            PriceLevel.EXPENSIVE -> "✋ Dyrt just nu – vänta om du kan"
            else -> null
        }
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = level.color())
        }

        Details(expanded) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(4.dp))
            val cur = dash.current
            if (cur != null) {
                DetailLine("Gäller ${Fmt.time(cur.start)}–${Fmt.time(cur.end)}")
                DetailLine("Spotpris ${Fmt.perKwh(cur.spot)} exkl. moms och avgifter · ${dash.area}")
            }
            val min = dash.todayMinTotal
            val max = dash.todayMaxTotal
            val avg = dash.todayAvgTotal
            if (min != null && max != null && avg != null) {
                DetailLine("Idag: lägst ${Fmt.num(min)} · högst ${Fmt.num(max)} · snitt ${Fmt.num(avg)} kr/kWh")
            }
            DetailLine("Priset är inklusive påslag, nätavgift, energiskatt och moms.")
        }
        Spacer(Modifier.height(4.dp))
        ExpandToggle(expanded, { expanded = !expanded })
    }
}

@Composable
private fun LevelBadge(level: PriceLevel) {
    if (level == PriceLevel.UNKNOWN) return
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(level.color().copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(level.color()),
            )
            Spacer(Modifier.width(6.dp))
            Text(level.label, fontWeight = FontWeight.SemiBold, color = level.color())
        }
    }
}

// ---------------------------------------------------------------------------
// Billigaste timmarna (graf och timpriser under "Visa mer")
// ---------------------------------------------------------------------------

@Composable
private fun CheapHoursCard(dash: Dashboard, settings: PriceSettings) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val now = dash.now
    val todayRanges = Planner.ranges(dash.cheapToday)
    val tomorrowRanges = Planner.ranges(dash.cheapTomorrow)
    HemmaCard(container = LevelColors.Cheap.copy(alpha = 0.10f)) {
        Text(
            "Billigaste timmarna",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        NextCheapLine(todayRanges + tomorrowRanges, now, tomorrowKnown = dash.tomorrow.isNotEmpty())

        Spacer(Modifier.height(14.dp))
        DayBlock("Idag", todayRanges, Planner.hourly(dash.today), dash.cheapToday, now)

        Spacer(Modifier.height(16.dp))
        if (tomorrowRanges.isNotEmpty()) {
            DayBlock("Imorgon", tomorrowRanges, Planner.hourly(dash.tomorrow), dash.cheapTomorrow, now)
        } else {
            DayLabel("Imorgon")
            Text(
                "Priserna kommer ca kl 13",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Details(expanded) {
            Spacer(Modifier.height(10.dp))
            PriceChartSection(dash, settings)
            Spacer(Modifier.height(14.dp))
            Text(
                "Pris per timme (snitt)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            HourGrid("Idag", dash.cheapToday, settings, dash.now)
            if (dash.cheapTomorrow.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                HourGrid("Imorgon", dash.cheapTomorrow, settings, dash.now)
            }
        }
        Spacer(Modifier.height(4.dp))
        ExpandToggle(expanded, { expanded = !expanded }, moreLabel = "Visa graf och priser")
    }
}

/** "Billigt nu – till kl 05", "Nästa om 2 h 10 min" eller "Inga fler billiga timmar idag". */
@Composable
private fun NextCheapLine(ranges: List<TimeRange>, now: ZonedDateTime, tomorrowKnown: Boolean) {
    val nowS = now.toEpochSecond()
    val ongoing = ranges.firstOrNull { it.start.toEpochSecond() <= nowS && nowS < it.end.toEpochSecond() }
    val next = ranges.firstOrNull { it.start.toEpochSecond() > nowS }
    val text = when {
        ongoing != null -> "⚡ Billigt nu – till kl ${Fmt.endHour(ongoing.start, ongoing.end)}"
        next != null -> {
            val minutes = ((next.start.toEpochSecond() - nowS + 59) / 60).toInt()
            "Nästa om ${Fmt.duration(minutes)}"
        }
        tomorrowKnown -> "Inga fler billiga timmar"
        else -> "Inga fler billiga timmar idag"
    }
    val highlight = ongoing != null || next != null
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = if (highlight) LevelColors.Cheap else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

@Composable
private fun DayLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayBlock(
    label: String,
    ranges: List<TimeRange>,
    hours: List<HourSlot>,
    cheap: List<HourSlot>,
    now: ZonedDateTime,
) {
    DayLabel(label)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ranges.forEach { r -> RangePill(r, past = !r.end.isAfter(now)) }
    }
    Spacer(Modifier.height(10.dp))
    HourStrip(hours, cheap, now)
}

/** Stor grön "02–05". Passerade perioder tonas ner. */
@Composable
private fun RangePill(r: TimeRange, past: Boolean) {
    Box(
        Modifier
            .alpha(if (past) 0.35f else 1f)
            .clip(RoundedCornerShape(14.dp))
            .background(LevelColors.Cheap)
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(
            Fmt.range(r),
            color = Color.White,
            fontSize = 26.sp,
            lineHeight = 34.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Dygnet som 24 rutor: gröna = billigaste timmarna, ram = nuvarande timme. */
@Composable
private fun HourStrip(hours: List<HourSlot>, cheap: List<HourSlot>, now: ZonedDateTime) {
    if (hours.isNotEmpty()) {
        val cheapStarts = cheap.map { it.start.toEpochSecond() }.toSet()
        val nowS = now.toEpochSecond()
        val other = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        val marker = MaterialTheme.colorScheme.onSurface
        Row(
            Modifier
                .fillMaxWidth()
                .height(22.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            hours.forEach { h ->
                val start = h.start.toEpochSecond()
                val isCheap = start in cheapStarts
                val isNow = start <= nowS && nowS < h.end.toEpochSecond()
                val past = !h.end.isAfter(now)
                val shape = RoundedCornerShape(4.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .alpha(if (past && !isNow) 0.35f else 1f)
                        .clip(shape)
                        .background(if (isCheap) LevelColors.Cheap else other)
                        .then(if (isNow) Modifier.border(2.dp, marker, shape) else Modifier),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "24").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PriceChartSection(dash: Dashboard, settings: PriceSettings) {
    var showTomorrow by rememberSaveable { mutableStateOf(false) }
    val tomorrowAvailable = dash.tomorrow.isNotEmpty()
    val tomorrow = showTomorrow && tomorrowAvailable
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = !tomorrow, onClick = { showTomorrow = false }, label = { Text("Idag") })
        FilterChip(
            selected = tomorrow,
            onClick = { showTomorrow = true },
            enabled = tomorrowAvailable,
            label = { Text(if (tomorrowAvailable) "Imorgon" else "Imorgon (ca 13:00)") },
        )
    }
    Spacer(Modifier.height(8.dp))
    PriceChart(
        points = if (tomorrow) dash.tomorrow else dash.today,
        cheap = if (tomorrow) dash.cheapTomorrow else dash.cheapToday,
        settings = settings,
        now = dash.now,
    )
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        LegendDot(LevelColors.Cheap)
        Text(" Billigaste timmarna   ", style = MaterialTheme.typography.labelSmall)
        LegendDot(LevelColors.Expensive)
        Text(" Dyraste tredjedelen", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun LegendDot(color: Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun HourGrid(title: String, slots: List<HourSlot>, settings: PriceSettings, now: ZonedDateTime) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(4.dp))
    slots.chunked(2).forEach { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            row.forEach { slot ->
                val past = !slot.end.isAfter(now)
                Row(
                    Modifier
                        .weight(1f)
                        .alpha(if (past) 0.4f else 1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LevelColors.Cheap.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.hourSlot(slot), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(Fmt.kr(settings.totalPrice(slot.avgSpot)), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------
// Maskiner: en rad per maskin, tryck för detaljer
// ---------------------------------------------------------------------------

@Composable
private fun MachinesCard(dash: Dashboard, onOpenMachines: () -> Unit) {
    HemmaCard {
        Text(
            "Bästa tid att köra",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (dash.plans.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text("Inga maskiner inlagda än.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenMachines) { Text("Lägg till maskiner") }
        } else {
            Spacer(Modifier.height(4.dp))
            dash.plans.forEachIndexed { i, plan ->
                if (i > 0) HorizontalDivider()
                key(plan.machine.id) {
                    PlanRow(plan, dash.now)
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: MachinePlan, now: ZonedDateTime) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val m = plan.machine
    val best = plan.best
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(m.type.emoji, fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    m.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                when {
                    best == null -> Text(
                        "Väntar på priser",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    plan.startNowIsBest -> Text(
                        "Starta nu",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = LevelColors.Cheap,
                    )
                    else -> Text(
                        "Starta ${Fmt.dayTime(best.start, now)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (best != null) {
                Text(
                    Fmt.kr(best.cost),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.width(4.dp))
            Chevron(expanded, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Details(expanded) {
            Column(Modifier.padding(start = 38.dp, bottom = 12.dp)) {
                if (best == null) {
                    DetailLine("Prisdata räcker inte för en hel körning ännu.")
                } else {
                    DetailLine(
                        "Klar ${Fmt.dayTime(best.end, now)}" +
                            (plan.deadline?.let { " · måste vara klar ${Fmt.dayTime(it, now)}" } ?: ""),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Stat("Kostar då", Fmt.kr(best.cost), Modifier.weight(1f), emphasize = true)
                        Stat("Om du startar nu", plan.startNow?.let { Fmt.kr(it.cost) } ?: "–", Modifier.weight(1f))
                        val saving = plan.saving
                        Stat("Du sparar", if (saving != null && saving > 0.005) Fmt.kr(saving) else "–", Modifier.weight(1f))
                    }
                }
                val info = buildString {
                    append("${Fmt.num(plan.kwh, 1)} kWh · ${Fmt.duration(plan.minutes)}")
                    if (m.model.isNotBlank()) append(" · ${m.model}")
                }
                DetailLine(info)
                if (plan.mightGetCheaper) {
                    Text(
                        "Morgondagens priser kommer runt kl 13 – det kan bli billigare då.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ---------------------------------------------------------------------------
// Fel, tomt läge och sidfot
// ---------------------------------------------------------------------------

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    HemmaCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(message, color = MaterialTheme.colorScheme.onTertiaryContainer)
        TextButton(onClick = onRetry) { Text("Försök igen") }
    }
}

@Composable
private fun EmptyCard(onRetry: () -> Unit) {
    HemmaCard {
        Text("Inga elpriser ännu", style = MaterialTheme.typography.titleMedium)
        Text(
            "Kontrollera internetanslutningen och försök igen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text("Hämta priser") }
    }
}

@Composable
private fun Footer(state: UiState) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(onClick = { uri.openUri("https://www.elprisetjustnu.se") }) {
            Text("Elpriser tillhandahålls av Elpriset just nu.se", style = MaterialTheme.typography.labelMedium)
        }
        if (state.prices.fetchedAtMillis > 0) {
            val t = Instant.ofEpochMilli(state.prices.fetchedAtMillis).atZone(se.hemma.app.data.STOCKHOLM)
            Text(
                "Uppdaterad ${Fmt.time(t)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
