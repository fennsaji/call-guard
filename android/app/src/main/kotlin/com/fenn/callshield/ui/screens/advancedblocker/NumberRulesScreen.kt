package com.fenn.callshield.ui.screens.advancedblocker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenn.callshield.domain.model.BlockingPreset
import com.fenn.callshield.ui.theme.LocalDangerColor
import com.fenn.callshield.ui.theme.LocalSuccessColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberRulesScreen(
    onBack: () -> Unit,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToWhitelist: () -> Unit,
    onNavigateToPrefixRules: () -> Unit,
    onNavigateToPaywall: () -> Unit = {},
    viewModel: AdvancedBlockingViewModel = hiltViewModel(),
) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Number Rules") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Smart Rules section ───────────────────────────────────────────
            item {
                Text(
                    "Smart rules",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))

                // Auto-escalate — single card with toggle + expanded chips
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Auto-escalate repeated callers",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Add to blocklist after ${policy.autoEscalateThreshold} rejections",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Switch(
                                checked = policy.autoEscalateEnabled,
                                onCheckedChange = {
                                    viewModel.updatePolicy(
                                        policy.copy(autoEscalateEnabled = it, preset = BlockingPreset.CUSTOM)
                                    )
                                },
                            )
                        }

                        if (policy.autoEscalateEnabled) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            Text(
                                "Rejection threshold",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(2, 3, 5, 10).forEach { count ->
                                    FilterChip(
                                        selected = policy.autoEscalateThreshold == count,
                                        onClick = {
                                            viewModel.updatePolicy(
                                                policy.copy(
                                                    autoEscalateThreshold = count,
                                                    preset = BlockingPreset.CUSTOM,
                                                )
                                            )
                                        },
                                        label = { Text("$count") },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Blocklist Aging (Pro) ─────────────────────────────────────────
            item {
                if (isPro) {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "Blocklist aging",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Auto-expire entries after ${policy.blocklistAgingDays} days of silence",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                }
                                Switch(
                                    checked = policy.blocklistAgingEnabled,
                                    onCheckedChange = {
                                        viewModel.updatePolicy(
                                            policy.copy(blocklistAgingEnabled = it, preset = BlockingPreset.CUSTOM)
                                        )
                                    },
                                )
                            }

                            if (policy.blocklistAgingEnabled) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                                Text(
                                    "Expire after",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(7, 14, 30, 60, 90).forEach { days ->
                                        FilterChip(
                                            selected = policy.blocklistAgingDays == days,
                                            onClick = {
                                                viewModel.updatePolicy(
                                                    policy.copy(blocklistAgingDays = days, preset = BlockingPreset.CUSTOM)
                                                )
                                            },
                                            label = { Text("${days}d") },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    ProFeatureCard(
                        valueText = "Stop old entries from clogging your blocklist — auto-expire after days of silence",
                        upgradeLabel = "Unlock Blocklist Aging",
                        onUpgradeClick = onNavigateToPaywall,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Blocklist aging",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Auto-expire entries after 30 days of silence",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                            }
                            Switch(checked = false, onCheckedChange = {}, enabled = false)
                        }
                    }
                }
            }

            // ── Manual lists section ──────────────────────────────────────────
            item {
                Text(
                    "Manual lists",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                val danger = LocalDangerColor.current
                val success = LocalSuccessColor.current
                val primary = MaterialTheme.colorScheme.primary
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                ) {
                    ManualListRow(
                        icon = Icons.Outlined.Block,
                        iconColor = danger,
                        title = "Blocklist",
                        subtitle = "Numbers you've blocked manually",
                        onClick = onNavigateToBlocklist,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ManualListRow(
                        icon = Icons.Outlined.VerifiedUser,
                        iconColor = success,
                        title = "Whitelist",
                        subtitle = "Always-allow exceptions",
                        onClick = onNavigateToWhitelist,
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    ManualListRow(
                        icon = Icons.Outlined.FilterList,
                        iconColor = primary,
                        title = "Pattern Rules",
                        subtitle = "Block by prefix or number pattern",
                        badge = if (!isPro) ({ ProBadge("5 rules") }) else null,
                        onClick = onNavigateToPrefixRules,
                    )
                }
            }
        }
    }
}

@Composable
private fun ManualListRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    badge: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = iconColor,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                badge?.invoke()
            }
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
        )
    }
}
