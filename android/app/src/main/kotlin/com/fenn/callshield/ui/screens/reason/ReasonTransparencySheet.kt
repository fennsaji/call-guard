package com.fenn.callshield.ui.screens.reason

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fenn.callshield.ui.theme.LocalSuccessColor
import com.fenn.callshield.R
import com.fenn.callshield.data.local.entity.CallHistoryEntry
import com.fenn.callshield.domain.model.DecisionSource
import com.fenn.callshield.ui.theme.LocalDangerColor
import com.fenn.callshield.ui.theme.LocalWarningColor
import kotlinx.coroutines.launch

/**
 * Bottom sheet shown when user taps a flagged/silenced call in the history list.
 * Shows matched prefix rule, seed DB presence, or behavioral signal reason.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReasonTransparencySheet(
    entry: CallHistoryEntry,
    onDismiss: () -> Unit,
    onMarkNotSpam: () -> Unit,
    onReport: () -> Unit,
    onTraiReported: () -> Unit = {},
    isBlocked: Boolean = false,
    isWhitelisted: Boolean = false,
    onBlock: () -> Unit = {},
    onUnblock: () -> Unit = {},
    onWhitelist: () -> Unit = {},
    onUnwhitelist: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dangerColor = LocalDangerColor.current
    val warningColor = LocalWarningColor.current
    val successColor = LocalSuccessColor.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Text(
                text = stringResource(R.string.reason_panel_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = entry.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )

            // Risk pill badge at the top
            val (riskLabel, riskColor) = when (entry.outcome) {
                "rejected", "silenced" -> {
                    if (entry.decisionSource == DecisionSource.SEED_DB.name)
                        stringResource(R.string.call_outcome_known_spam) to dangerColor
                    else
                        stringResource(R.string.call_outcome_likely_spam) to warningColor
                }
                "flagged" -> stringResource(R.string.call_outcome_likely_spam) to warningColor
                else -> stringResource(R.string.call_outcome_unknown) to MaterialTheme.colorScheme.onSurface
            }

            Surface(
                color = riskColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = riskLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = riskColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            HorizontalDivider()

            // Reason rows
            val source = DecisionSource.entries.firstOrNull { it.name == entry.decisionSource }
                ?: DecisionSource.DEFAULT

            when (source) {
                DecisionSource.SEED_DB -> {
                    ReasonRow(
                        icon = Icons.Filled.Shield,
                        tint = dangerColor,
                        text = stringResource(R.string.reason_seed_db),
                    )
                }
                DecisionSource.PREFIX -> {
                    ReasonRow(
                        icon = Icons.Filled.Block,
                        tint = dangerColor,
                        text = stringResource(R.string.reason_prefix_matched),
                    )
                }
                DecisionSource.HIDDEN -> {
                    ReasonRow(
                        icon = Icons.Filled.VisibilityOff,
                        tint = MaterialTheme.colorScheme.onSurface,
                        text = stringResource(R.string.reason_hidden),
                    )
                }
                DecisionSource.BLOCKLIST -> {
                    ReasonRow(
                        icon = Icons.Filled.Block,
                        tint = dangerColor,
                        text = stringResource(R.string.reason_blocklist),
                    )
                }
                DecisionSource.BEHAVIORAL -> {
                    when (entry.category) {
                        "burst_pattern" -> ReasonRow(
                            icon = Icons.Filled.Repeat,
                            tint = warningColor,
                            text = "Rapid repeated calls detected in the last 15 minutes",
                        )
                        "frequency_anomaly" -> ReasonRow(
                            icon = Icons.Filled.Repeat,
                            tint = warningColor,
                            text = "Called 3 or more times in the last hour",
                        )
                        "short_ring" -> ReasonRow(
                            icon = Icons.Filled.Timer,
                            tint = warningColor,
                            text = "Multiple short-ring calls detected — possible bait-and-callback scam",
                        )
                        else -> ReasonRow(
                            icon = Icons.Filled.Warning,
                            tint = warningColor,
                            text = "Suspicious call pattern detected",
                        )
                    }
                }
                else -> {
                    ReasonRow(
                        icon = Icons.Filled.Info,
                        tint = MaterialTheme.colorScheme.onSurface,
                        text = source.displayLabel,
                    )
                }
            }

            // Show category only for reputation-based decisions (not behavioral, where
            // category encodes the signal type and is already shown in the reason row)
            if (source != DecisionSource.BEHAVIORAL) {
                entry.category?.let { cat ->
                    Text(
                        text = "Category: ${cat.replace('_', ' ').replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // Block / Whitelist toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isBlocked) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch { sheetState.hide(); onDismiss(); onUnblock() }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = dangerColor.copy(alpha = 0.10f),
                            contentColor = dangerColor,
                        ),
                    ) {
                        Icon(Icons.Filled.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Unblock")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            scope.launch { sheetState.hide(); onDismiss(); onBlock() }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Block")
                    }
                }

                if (isWhitelisted) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch { sheetState.hide(); onDismiss(); onUnwhitelist() }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = successColor.copy(alpha = 0.10f),
                            contentColor = successColor,
                        ),
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Whitelisted")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            scope.launch { sheetState.hide(); onDismiss(); onWhitelist() }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Whitelist")
                    }
                }
            }

            // Mark as Not Spam / Report
            Button(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onMarkNotSpam()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                ),
            ) {
                Text(stringResource(R.string.not_spam_undo))
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onReport()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.report_title))
            }

        }
    }
}


@Composable
private fun ReasonRow(
    icon: ImageVector,
    tint: Color,
    text: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = tint)
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}
