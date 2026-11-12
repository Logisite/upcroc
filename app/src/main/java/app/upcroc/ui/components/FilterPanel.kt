package app.upcroc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.upcroc.core.model.SearchFilters
import app.upcroc.ui.icons.UiIcon
import app.upcroc.ui.theme.UpType
import app.upcroc.ui.theme.Upcroc

/**
 * The one shared filter panel used by all three search slots. It stays a plain
 * column so it can sit inside a bottom sheet without nested scrolling.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterPanel(
    filters: SearchFilters,
    onChange: (SearchFilters) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        AppText("Shared filters", style = UpType.section, color = Upcroc.Text)
        Spacer(Modifier.height(3.dp))
        AppText(
            "Every search uses this same set. Smart search ignores some of them by design.",
            style = UpType.small,
            color = Upcroc.Muted,
        )
        Spacer(Modifier.height(16.dp))

        FilterLabel("Job type")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Any", active = filters.jobType == null) { onChange(filters.copy(jobType = null)) }
            Chip("Hourly", active = filters.jobType == "Hourly") {
                onChange(filters.copy(jobType = if (filters.jobType == "Hourly") null else "Hourly"))
            }
            Chip("Fixed", active = filters.jobType == "Fixed") {
                onChange(filters.copy(jobType = if (filters.jobType == "Fixed") null else "Fixed"))
            }
        }

        FilterLabel("Experience")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Any", active = filters.experienceLevel == null) {
                onChange(filters.copy(experienceLevel = null))
            }
            listOf("Entry", "Intermediate", "Expert").forEach { level ->
                Chip(level, active = filters.experienceLevel == level) {
                    onChange(
                        filters.copy(
                            experienceLevel = if (filters.experienceLevel == level) null else level
                        )
                    )
                }
            }
        }

        FilterLabel("Hourly rate")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberField(
                label = "Min $/hr",
                value = filters.rateMin,
                onValueChange = { onChange(filters.copy(rateMin = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Max $/hr",
                value = filters.rateMax,
                onValueChange = { onChange(filters.copy(rateMax = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        FilterLabel("Fixed budget")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberField(
                label = "Min $",
                value = filters.budgetMin,
                onValueChange = { onChange(filters.copy(budgetMin = it)) },
                modifier = Modifier.weight(1f),
            )
            NumberField(
                label = "Max $",
                value = filters.budgetMax,
                onValueChange = { onChange(filters.copy(budgetMax = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        FilterLabel("Proposals and client")
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            NumberField(
                label = "Proposals max",
                value = filters.proposalsMax?.toDouble(),
                onValueChange = { onChange(filters.copy(proposalsMax = it?.toInt())) },
                modifier = Modifier.weight(1f),
                integer = true,
            )
            NumberField(
                label = "Client hires min",
                value = filters.clientHiresMin?.toDouble(),
                onValueChange = { onChange(filters.copy(clientHiresMin = it?.toInt())) },
                modifier = Modifier.weight(1f),
                integer = true,
            )
        }
        Spacer(Modifier.height(7.dp))
        UpField(
            label = "Client location",
            value = filters.clientLocation.orEmpty(),
            onValueChange = { onChange(filters.copy(clientLocation = it.ifBlank { null })) },
            placeholder = "Anywhere",
            modifier = Modifier.fillMaxWidth(),
        )

        FilterLabel("Words")
        UpField(
            label = "Must include",
            value = filters.includeKeywords,
            onValueChange = { onChange(filters.copy(includeKeywords = it)) },
            placeholder = "n8n, automation",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(7.dp))
        UpField(
            label = "Never include",
            value = filters.excludeKeywords,
            onValueChange = { onChange(filters.copy(excludeKeywords = it)) },
            placeholder = "unpaid, commission only",
            modifier = Modifier.fillMaxWidth(),
        )

        FilterLabel("Project duration")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip("Any", active = filters.duration == null) { onChange(filters.copy(duration = null)) }
            listOf("Ongoing", "Less than 1 month", "1 to 3 months", "3 to 6 months", "6+ months")
                .forEach { duration ->
                    Chip(duration, active = filters.duration == duration) {
                        onChange(
                            filters.copy(duration = if (filters.duration == duration) null else duration)
                        )
                    }
                }
        }

        Spacer(Modifier.height(14.dp))
        SwitchRow(
            title = "Verified payment only",
            subtitle = "Hide clients without a verified payment method.",
            checked = filters.verifiedPaymentOnly,
            onCheckedChange = { onChange(filters.copy(verifiedPaymentOnly = it)) },
            background = Upcroc.SurfaceHigh,
        )

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton("Reset", onReset, Modifier.weight(1f))
            PrimaryButton("Apply filters", onApply, Modifier.weight(1f), icon = UiIcon.Check)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun FilterLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    AppText(
        text.uppercase(),
        style = UpType.tiny.copy(fontWeight = FontWeight.W700, letterSpacing = 0.6.sp),
        color = Upcroc.Dim,
    )
    Spacer(Modifier.height(7.dp))
}
