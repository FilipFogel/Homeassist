@file:OptIn(ExperimentalMaterial3Api::class)

package se.hemma.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import se.hemma.app.data.PriceSettings
import se.hemma.app.domain.Dashboard
import se.hemma.app.domain.Fmt
import se.hemma.app.domain.HourSlot
import se.hemma.app.domain.MachinePlan
import se.hemma.app.domain.PriceLevel
import se.hemma.app.ui.theme.LevelColors
import se.hemma.app.ui.theme.color
import java.time.Instant
import java.time.ZonedDateTime

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
        item { NowCard(dash) }
        item { ChartCard(dash, state.settings) }
        item { CheapHoursCard(dash, state.settings) }
        item { SectionTitle("Bästa tid att köra") }
        if (dash.plans.isEmpty()) {
            item {
                HemmaCard {
                    Text("Inga maskiner inlagda än.")
                    TextButton(onClick = onOpenMachines) { Text("Lägg till maskiner") }
                }
            }
        }
        items(dash.plans, key = { it.machine.id }) { plan -> PlanCard(plan, dash.now) }
        item { Footer(state) }
    }
}

@Composable
private fun NowCard(dash: Dashboard) {
    val level = dash.level
    HemmaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val cur = dash.current
                Text(
                    if (cur != null) "Just nu · ${Fmt.time(cur.start)}–${Fmt.time(cur.end)}" else "Just nu",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    dash.currentTotal?.let { Fmt.perKwh(it) } ?: "–",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 44.sp,
                )
                if (cur != null) {
                    Text(
                        "Spotpris ${Fmt.perKwh(cur.spot)} exkl. moms · ${dash.area}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LevelBadge(level)
        }
        val min = dash.todayMinTotal
        val max = dash.todayMaxTotal
        val avg = dash.todayAvgTotal
        if (min != null && max != null && avg != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Idag ${Fmt.num(min)}–${Fmt.num(max)} kr/kWh · snitt ${Fmt.num(avg)} kr",
                style = MaterialTheme.typography.bodyMedium,
            )
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

@Composable
private fun ChartCard(dash: Dashboard, settings: PriceSettings) {
    var showTomorrow by rememberSaveable { mutableStateOf(false) }
    val tomorrowAvailable = dash.tomorrow.isNotEmpty()
    val tomorrow = showTomorrow && tomorrowAvailable
    HemmaCard {
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
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color) {
    Box(
        Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun CheapHoursCard(dash: Dashboard, settings: PriceSettings) {
    HemmaCard {
        Text(
            "De ${settings.cheapHoursCount} billigaste timmarna",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        HourGrid("Idag", dash.cheapToday, settings, dash.now)
        if (dash.cheapTomorrow.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HourGrid("Imorgon", dash.cheapTomorrow, settings, dash.now)
        } else {
            Spacer(Modifier.height(6.dp))
            Text(
                "Morgondagens priser publiceras runt kl 13.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourGrid(title: String, slots: List<HourSlot>, settings: PriceSettings, now: ZonedDateTime) {
    Text(
        "$title · ${Fmt.hourRanges(slots)}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
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

@Composable
private fun PlanCard(plan: MachinePlan, now: ZonedDateTime) {
    val m = plan.machine
    HemmaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.type.emoji, fontSize = 30.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val details = buildString {
                    append("${Fmt.num(plan.kwh, 1)} kWh · ${Fmt.duration(plan.minutes)}")
                    if (m.model.isNotBlank()) append(" · ${m.model}")
                }
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(12.dp))

        val best = plan.best
        if (best == null) {
            Text(
                "Prisdata räcker inte för en hel körning ännu.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            if (plan.startNowIsBest) {
                Text("Starta nu", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = LevelColors.Cheap)
            } else {
                Text(
                    "Starta ${Fmt.dayTime(best.start, now)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Klar ${Fmt.dayTime(best.end, now)}" + (plan.deadline?.let { " · måste vara klar ${Fmt.dayTime(it, now)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Stat("Kostar då", Fmt.kr(best.cost), Modifier.weight(1f), emphasize = true)
                Stat("Om du startar nu", plan.startNow?.let { Fmt.kr(it.cost) } ?: "–", Modifier.weight(1f))
                val saving = plan.saving
                Stat("Du sparar", if (saving != null && saving > 0.005) Fmt.kr(saving) else "–", Modifier.weight(1f))
            }
        }
        if (plan.mightGetCheaper) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Morgondagens priser kommer runt kl 13 – det kan bli billigare då.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = if (emphasize) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

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
