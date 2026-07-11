package com.fenn.callshield.ui.screens.protect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.fenn.callshield.ui.screens.home.HomeViewModel
import com.fenn.callshield.ui.theme.LocalDangerColor
import com.fenn.callshield.ui.theme.LocalSuccessColor
import com.fenn.callshield.ui.theme.LocalWarningColor
import com.fenn.callshield.ui.screens.advancedblocker.AdvancedBlockingViewModel

@Composable
fun ProtectScreen(
    modifier: Modifier = Modifier,
    onNavigateToAdvancedBlocking: () -> Unit,
    onNavigateToBlocklist: () -> Unit,
    onNavigateToWhitelist: () -> Unit,
    onNavigateToPrefixRules: () -> Unit,
    onNavigateToDndManagement: () -> Unit,
    onNavigateToPaywall: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    advancedBlockingViewModel: AdvancedBlockingViewModel = hiltViewModel(),
) {
    val state by homeViewModel.uiState.collectAsStateWithLifecycle()
    val isPro by homeViewModel.isPro.collectAsStateWithLifecycle()
    val policy by advancedBlockingViewModel.policy.collectAsStateWithLifecycle()
    val dangerColor = LocalDangerColor.current
    val successColor = LocalSuccessColor.current
    val warningColor = LocalWarningColor.current
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        item {
            Text(
                text = "Protect",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        // ── Quick Access ──────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Quick Access")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuickAccessCard(
                        icon = Icons.Outlined.Block,
                        label = "Blocklist",
                        iconTint = dangerColor,
                        onClick = onNavigateToBlocklist,
                        modifier = Modifier.weight(1f),
                    )
                    QuickAccessCard(
                        icon = Icons.Outlined.CheckCircle,
                        label = "Whitelist",
                        iconTint = successColor,
                        onClick = onNavigateToWhitelist,
                        modifier = Modifier.weight(1f),
                    )
                    QuickAccessCard(
                        icon = Icons.Outlined.FilterList,
                        label = "Patterns",
                        iconTint = primary,
                        onClick = onNavigateToPrefixRules,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ── Blocking ──────────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Blocking")
                AdvancedBlockingCard(
                    presetName = policy.preset.displayName(),
                    presetIcon = policy.preset.icon(),
                    onClick = onNavigateToAdvancedBlocking,
                )
                if (state.isIndiaDevice) {
                    FeatureRowCard(
                        icon = Icons.Outlined.DoNotDisturb,
                        title = "DND Management",
                        subtitle = "Block telemarketers via India's DND registry",
                        iconTint = warningColor,
                        onClick = onNavigateToDndManagement,
                    )
                }
            }
        }

        // ── Controls ──────────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Controls")
                ProtectionToggleCard(
                    icon = Icons.Outlined.VisibilityOff,
                    title = "Block hidden numbers",
                    description = "Reject calls from private or hidden callers",
                    color = tertiary,
                    checked = state.blockHidden,
                    isPro = isPro,
                    onCheckedChange = { homeViewModel.setBlockHidden(it) },
                    onLockedClick = onNavigateToPaywall,
                )
            }
        }
    }
}

@Composable
private fun AdvancedBlockingCard(
    presetName: String,
    presetIcon: ImageVector,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    presetIcon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = primary,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    "Advanced Blocking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Current: $presetName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "Configure",
                    style = MaterialTheme.typography.labelSmall,
                    color = primary,
                )
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = primary,
                )
            }
        }
    }
}

@Composable
private fun ProtectionToggleCard(
    icon: ImageVector,
    title: String,
    description: String,
    color: Color,
    checked: Boolean,
    isPro: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onLockedClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isPro) Modifier.clickable(onClick = onLockedClick) else Modifier),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = color)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (isPro) {
                Switch(checked = checked, onCheckedChange = onCheckedChange)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Pro", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QuickAccessCard(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.07f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = iconTint)
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun FeatureRowCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    iconTint: Color = Color.Unspecified,
    isLocked: Boolean = false,
    lockLabel: String = "Pro",
    onLockedClick: () -> Unit = {},
) {
    val effectiveClick = if (isLocked) onLockedClick else onClick
    val resolvedTint = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.onSurfaceVariant else iconTint
    val iconBg = if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.surfaceVariant else iconTint.copy(alpha = 0.07f)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = effectiveClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = resolvedTint)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            if (isLocked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(lockLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
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

private fun BlockingPreset.icon(): ImageVector = when (this) {
    BlockingPreset.BALANCED -> Icons.Filled.Balance
    BlockingPreset.AGGRESSIVE -> Icons.Filled.Shield
    BlockingPreset.CONTACTS_ONLY -> Icons.Filled.ContactPhone
    BlockingPreset.NIGHT_GUARD -> Icons.Filled.DarkMode
    BlockingPreset.INTERNATIONAL_LOCK -> Icons.Filled.Language
    BlockingPreset.CUSTOM -> Icons.Filled.Tune
}
