package com.fenn.callshield.ui.screens.advancedblocker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import kotlinx.coroutines.delay
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedBlockingScreen(
    onBack: () -> Unit,
    onNavigateToContactPolicies: () -> Unit,
    onNavigateToTimePolicies: () -> Unit,
    onNavigateToRegionPolicies: () -> Unit,
    onNavigateToNumberRules: () -> Unit,
    onNavigateToDecisionOrder: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    viewModel: AdvancedBlockingViewModel = hiltViewModel(),
) {
    val policy by viewModel.policy.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(policy.preset) {
        if (policy.preset == BlockingPreset.CUSTOM) {
            // Wait one frame for the custom section item to enter the layout
            delay(50)
            val offset = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == 1 }?.offset?.toFloat()
            if (offset != null && offset > 0f) {
                listState.animateScrollBy(
                    value = offset,
                    animationSpec = tween(durationMillis = 600),
                )
            } else {
                listState.animateScrollToItem(1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Blocking") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Preset picker ─────────────────────────────────────────────────
            item {
                SectionHeader("Blocking Preset")
                Spacer(Modifier.height(10.dp))
                PresetGrid(
                    currentPreset = policy.preset,
                    isPro = isPro,
                    onSelect = { viewModel.setPreset(it) },
                    onNavigateToPaywall = onNavigateToPaywall,
                )
            }

            // ── Custom policy sections (only when CUSTOM) ─────────────────────
            if (policy.preset == BlockingPreset.CUSTOM) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    SectionHeader("Custom Policy Groups")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PolicyGroupCard(
                            icon = Icons.Filled.ContactPhone,
                            title = "Contact Policies",
                            description = "Allow contacts only, silence unknown callers",
                            onClick = onNavigateToContactPolicies,
                        )
                        PolicyGroupCard(
                            icon = Icons.Filled.DarkMode,
                            title = "Time Policies",
                            description = "Night Guard — silence calls during specific hours",
                            onClick = onNavigateToTimePolicies,
                        )
                        PolicyGroupCard(
                            icon = Icons.Filled.Language,
                            title = "Region Policies",
                            description = "Block international callers",
                            onClick = onNavigateToRegionPolicies,
                        )
                        PolicyGroupCard(
                            icon = Icons.Filled.Security,
                            title = "Number Rules",
                            description = "Auto-expire stale blocklist entries",
                            onClick = onNavigateToNumberRules,
                        )
                    }
                }
            }

            // ── Decision order ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onNavigateToDecisionOrder,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("View Decision Order")
                }
            }
        }
    }
}

private data class PresetEntry(
    val preset: BlockingPreset,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun PresetGrid(
    currentPreset: BlockingPreset,
    isPro: Boolean,
    onSelect: (BlockingPreset) -> Unit,
    onNavigateToPaywall: () -> Unit,
) {
    val presets = listOf(
        PresetEntry(BlockingPreset.BALANCED, Icons.Filled.Balance, "Balanced"),
        PresetEntry(BlockingPreset.AGGRESSIVE, Icons.Filled.Security, "Aggressive"),
        PresetEntry(BlockingPreset.CONTACTS_ONLY, Icons.Filled.ContactPhone, "Contacts Only"),
        PresetEntry(BlockingPreset.NIGHT_GUARD, Icons.Filled.DarkMode, "Night Guard"),
        PresetEntry(BlockingPreset.INTERNATIONAL_LOCK, Icons.Filled.Language, "International Lock"),
        PresetEntry(BlockingPreset.CUSTOM, Icons.Filled.Tune, "Custom"),
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        presets.forEach { entry ->
            val isProLocked = entry.preset == BlockingPreset.CUSTOM && !isPro
            PresetCard(
                icon = entry.icon,
                label = entry.label,
                description = entry.preset.description(),
                details = entry.preset.details(),
                isSelected = currentPreset == entry.preset,
                isProLocked = isProLocked,
                onClick = { onSelect(entry.preset) },
                onLockedClick = onNavigateToPaywall,
            )
        }
    }
}

@Composable
private fun PresetCard(
    icon: ImageVector,
    label: String,
    description: String,
    details: List<String>,
    isSelected: Boolean,
    isProLocked: Boolean = false,
    onClick: () -> Unit,
    onLockedClick: () -> Unit = {},
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isSelected) primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = if (isProLocked) onLockedClick else onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) primary.copy(alpha = 0.08f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isSelected) primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                if (isProLocked) {
                    ProBadge()
                } else if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(primary),
                    )
                }
            }

            if ((isSelected || isProLocked) && details.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = primary.copy(alpha = 0.2f))
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    details.forEach { line ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                Icons.Filled.Circle,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(5.dp),
                                tint = primary.copy(alpha = 0.7f),
                            )
                            Text(
                                line,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PolicyGroupCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
    )
}

private fun BlockingPreset.displayName(): String = when (this) {
    BlockingPreset.BALANCED -> "Balanced"
    BlockingPreset.AGGRESSIVE -> "Aggressive"
    BlockingPreset.CONTACTS_ONLY -> "Contacts Only"
    BlockingPreset.NIGHT_GUARD -> "Night Guard"
    BlockingPreset.INTERNATIONAL_LOCK -> "International Lock"
    BlockingPreset.CUSTOM -> "Custom"
}

private fun BlockingPreset.description(): String = when (this) {
    BlockingPreset.BALANCED -> "Detects known spam. Unknown numbers ring normally."
    BlockingPreset.AGGRESSIVE -> "Silences every unknown caller — only your saved contacts ring through"
    BlockingPreset.CONTACTS_ONLY -> "Only your saved contacts can reach you"
    BlockingPreset.NIGHT_GUARD -> "No unknown calls during your sleep hours"
    BlockingPreset.INTERNATIONAL_LOCK -> "Blocks calls from outside your country"
    BlockingPreset.CUSTOM -> "Build your own rules from scratch"
}

private fun BlockingPreset.details(): List<String> = when (this) {
    BlockingPreset.BALANCED -> listOf(
        "Known spam numbers are silenced based on community reports",
        "All other calls ring normally — nothing extra is blocked",
        "Good starting point for most users",
    )
    BlockingPreset.AGGRESSIVE -> listOf(
        "Unknown callers are silenced — you won't hear the phone ring",
        "You can still see who called and call them back if needed",
        "Only numbers already in your contacts ring through normally",
    )
    BlockingPreset.CONTACTS_ONLY -> listOf(
        "Only people saved in your contacts can call you",
        "Everyone else gets a busy signal immediately — they can't even make it ring",
        "Great if you only want calls from people you know",
    )
    BlockingPreset.NIGHT_GUARD -> listOf(
        "Unknown callers are silenced between 10 PM and 7 AM",
        "Your contacts can still reach you at any hour",
        "Calls are not blocked permanently — just silenced during quiet hours",
        "Custom sleep hours available with Pro",
    )
    BlockingPreset.INTERNATIONAL_LOCK -> listOf(
        "Calls from outside your country are silenced automatically",
        "International numbers saved in your contacts are still allowed through",
        "Ideal if you rarely or never expect calls from abroad",
    )
    BlockingPreset.CUSTOM -> listOf(
        "Choose who can call you: contacts only, silence unknowns, or allow all",
        "Set quiet hours to silence unknown calls at night",
        "Block international numbers",
        "Auto-expire stale blocklist entries",
    )
}
