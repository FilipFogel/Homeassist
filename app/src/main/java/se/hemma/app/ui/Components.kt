package se.hemma.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import se.hemma.app.domain.Fmt

@Composable
fun HemmaCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** Textfält för tal som accepterar både komma och punkt. Anropar [onValue] bara med giltiga värden. */
@Composable
fun DecimalField(
    label: String,
    value: Double,
    suffix: String,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
    min: Double = 0.0,
) {
    var text by remember { mutableStateOf(Fmt.input(value)) }
    val parsed = Fmt.parse(text)
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            Fmt.parse(t)?.let { if (it >= min) onValue(it) }
        },
        label = { Text(label) },
        suffix = { Text(suffix) },
        isError = parsed == null || parsed < min,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth(),
    )
}
