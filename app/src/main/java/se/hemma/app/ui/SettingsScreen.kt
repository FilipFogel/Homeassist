@file:OptIn(ExperimentalMaterial3Api::class)

package se.hemma.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import se.hemma.app.data.PriceSettings
import se.hemma.app.domain.Dashboard
import se.hemma.app.domain.Fmt

private val AREAS = listOf(
    "SE1" to "Luleå",
    "SE2" to "Sundsvall",
    "SE3" to "Stockholm",
    "SE4" to "Malmö",
)

@Composable
fun SettingsScreen(
    settings: PriceSettings,
    dashboard: Dashboard?,
    onChange: (PriceSettings) -> Unit,
) {
    val uri = LocalUriHandler.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HemmaCard {
            Text("Elområde", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AREAS.forEach { (code, _) ->
                    FilterChip(
                        selected = settings.area == code,
                        onClick = { onChange(settings.copy(area = code)) },
                        label = { Text(code) },
                    )
                }
            }
            Text(
                AREAS.joinToString(" · ") { "${it.first} ${it.second}" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        HemmaCard {
            Text("Ditt elpris", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Räkna med skatt och avgifter")
                    Text(
                        if (settings.includeFees) "Visar vad elen faktiskt kostar dig" else "Visar spotpris inkl. moms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.includeFees,
                    onCheckedChange = { onChange(settings.copy(includeFees = it)) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("Elhandelns påslag", settings.supplierMarkupOre, "öre/kWh", { onChange(settings.copy(supplierMarkupOre = it)) })
                DecimalField("Elnätets överföringsavgift", settings.gridFeeOre, "öre/kWh", { onChange(settings.copy(gridFeeOre = it)) })
                DecimalField("Energiskatt", settings.energyTaxOre, "öre/kWh", { onChange(settings.copy(energyTaxOre = it)) })
                DecimalField("Moms", settings.vatPercent, "%", { onChange(settings.copy(vatPercent = it)) })
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Ange avgifterna exkl. moms – de står på fakturorna från elnätsbolaget och elhandlaren. " +
                    "Energiskatten är 36 öre/kWh under 2026 (lägre i vissa kommuner i norra Sverige). " +
                    "Fasta månadsavgifter och effektavgifter påverkar inte vilken tid som är billigast.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val cur = dashboard?.current
            if (cur != null && settings.includeFees) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Just nu: (spot ${Fmt.num(cur.spot)} + avgifter ${Fmt.num(settings.feesSek())}) × " +
                        "${Fmt.num(1 + settings.vatPercent / 100)} = ${Fmt.perKwh(settings.totalPrice(cur.spot))}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        HemmaCard {
            Text("Billigaste timmarna", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Antal timmar att markera", modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { onChange(settings.copy(cheapHoursCount = (settings.cheapHoursCount - 1).coerceAtLeast(1))) },
                ) { Text("−") }
                Text(
                    "${settings.cheapHoursCount}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 14.dp),
                )
                FilledTonalButton(
                    onClick = { onChange(settings.copy(cheapHoursCount = (settings.cheapHoursCount + 1).coerceAtMost(12))) },
                ) { Text("+") }
            }
        }

        HemmaCard {
            Text("Om", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Priserna är Nord Pools day-ahead-priser per kvart, hämtade gratis från Elpriset just nu.se. " +
                    "Morgondagens priser publiceras runt kl 13. Allt sparas bara i telefonen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { uri.openUri("https://www.elprisetjustnu.se") }) { Text("elprisetjustnu.se") }
        }
    }
}
