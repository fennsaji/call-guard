package com.fenn.callshield.ui.screens.report

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fenn.callshield.R
import com.fenn.callshield.domain.model.SpamCategory
import com.fenn.callshield.ui.components.AppDialog
import com.fenn.callshield.ui.components.SmsCommandRow
import com.fenn.callshield.util.TraiReportHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val categoryIcons: Map<SpamCategory, ImageVector> = mapOf(
    SpamCategory.TELEMARKETING to Icons.Filled.Campaign,
    SpamCategory.LOAN_SCAM to Icons.Filled.MoneyOff,
    SpamCategory.INVESTMENT_SCAM to Icons.Filled.TrendingUp,
    SpamCategory.IMPERSONATION to Icons.Filled.AccountBalance,
    SpamCategory.PHISHING to Icons.Filled.GppBad,
    SpamCategory.JOB_SCAM to Icons.Filled.Work,
    SpamCategory.OTHER to Icons.Filled.Category,
)

private val categoryDescriptions: Map<SpamCategory, String> = mapOf(
    SpamCategory.TELEMARKETING to "Promotional or sales call you didn't request",
    SpamCategory.LOAN_SCAM to "Fake loan offers or financial fraud",
    SpamCategory.INVESTMENT_SCAM to "Fraudulent investment scheme or stock tip",
    SpamCategory.IMPERSONATION to "Caller pretending to be bank, government or official",
    SpamCategory.PHISHING to "Attempt to steal passwords or personal info",
    SpamCategory.JOB_SCAM to "Fake job or work-from-home offer",
    SpamCategory.OTHER to "Any other unwanted or suspicious call",
)

/**
 * 7 days in milliseconds — TRAI Complaint vs Report threshold.
 * Updated by TCCCPR Feb 2025 amendment (was 3 days prior).
 * Within 7 days = Complaint (actionable). After 7 days = Report (data only).
 */
private val TRAI_COMPLAINT_WINDOW_MS = TimeUnit.DAYS.toMillis(7)

private data class PendingTraiReport(
    val smsBody: String,
    val isComplaint: Boolean,
    val onConfirm: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSpamScreen(
    numberHash: String,
    displayLabel: String,
    screenedAt: Long = 0L,
    onDismiss: () -> Unit,
    viewModel: ReportSpamViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedCategory by rememberSaveable { mutableStateOf<SpamCategory?>(null) }
    var description by rememberSaveable { mutableStateOf("") }
    var isDescriptionEdited by rememberSaveable { mutableStateOf(false) }
    val isIndiaDevice = state.isIndiaDevice
    var reportToTrai by rememberSaveable { mutableStateOf(true) }
    var pendingTraiReport by remember { mutableStateOf<PendingTraiReport?>(null) }

    // Whether the call is within TRAI's 7-day complaint window
    val isWithinComplaintWindow = screenedAt > 0L &&
        (System.currentTimeMillis() - screenedAt) < TRAI_COMPLAINT_WINDOW_MS

    // Auto-fill description whenever category changes, unless user manually edited it
    LaunchedEffect(selectedCategory) {
        if (!isDescriptionEdited) {
            description = selectedCategory
                ?.let { "Received spam call in the category: ${it.displayName}" }
                ?: ""
        }
    }

    LaunchedEffect(state.submitted) {
        if (state.submitted) {
            if (isIndiaDevice && reportToTrai) {
                val callDate = SimpleDateFormat("dd/MM/yy", Locale("en", "IN"))
                    .format(Date(if (screenedAt > 0L) screenedAt else System.currentTimeMillis()))
                val base = description.ifBlank { "Received unsolicited spam call" }
                val smsBody = "$base, $displayLabel, $callDate"
                pendingTraiReport = PendingTraiReport(
                    smsBody = smsBody,
                    isComplaint = isWithinComplaintWindow,
                    onConfirm = {
                        viewModel.saveTraiReport(numberHash, displayLabel)
                        try {
                            context.startActivity(TraiReportHelper.createSmsIntent(smsBody))
                        } catch (_: Exception) { /* SMS app not available */ }
                        onDismiss()
                    },
                )
            } else {
                onDismiss()
            }
        }
    }

    // Confirmation dialog — shown after API success when TRAI redirect is enabled
    pendingTraiReport?.let { pending ->
        val complaintLabel = if (pending.isComplaint) "Complaint" else "Report"
        AppDialog(
            onDismissRequest = {
                pendingTraiReport = null
                onDismiss()
            },
            icon = Icons.Filled.BugReport,
            title = "Report submitted — notify TRAI?",
            confirmLabel = "Open SMS App",
            onConfirm = {
                pendingTraiReport = null
                pending.onConfirm()
            },
            dismissLabel = "Skip",
            onDismiss = {
                pendingTraiReport = null
                onDismiss()
            },
        ) {
            Text(
                text = if (pending.isComplaint)
                    "Within 7 days — TRAI will treat this as a Complaint and can take regulatory action against the sender."
                else
                    "After 7 days — TRAI will record this as a Report (data only). No action is taken against the sender.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
            SmsCommandRow(
                label = "SMS to 1909 ($complaintLabel):",
                command = pending.smsBody,
            )
            Text(
                text = "Your SMS app will open with this message pre-filled. Just tap Send.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                    Text(
                        text = "Reporting call from",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    Text(
                        text = displayLabel,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            // Category header
            item {
                Text(
                    text = "Select category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(4.dp))
            }

            // Category cards
            items(SpamCategory.entries) { category ->
                CategoryCard(
                    category = category,
                    icon = categoryIcons[category] ?: Icons.Filled.Category,
                    description = categoryDescriptions[category] ?: "",
                    selected = selectedCategory == category,
                    onSelect = { selectedCategory = category },
                )
            }

            // Description field
            item {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Description (optional)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; isDescriptionEdited = true },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "e.g. Caller claimed to be from SBI and asked for OTP",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        )
                    },
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    shape = MaterialTheme.shapes.medium,
                )
            }

            // TRAI report toggle — India only
            if (isIndiaDevice) {
                item {
                    Spacer(Modifier.height(4.dp))
                    TraiReportSection(
                        enabled = reportToTrai,
                        isWithinComplaintWindow = isWithinComplaintWindow,
                        onToggle = { reportToTrai = it },
                    )
                }
            }

            // Submit button
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        selectedCategory?.let {
                            viewModel.submitReport(numberHash, displayLabel)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    enabled = selectedCategory != null,
                ) {
                    Text(
                        stringResource(R.string.report_submit),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: SpamCategory,
    icon: ImageVector,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = if (selected) primaryColor else MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.medium,
            )
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = if (selected) primaryColor
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (selected) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            RadioButton(
                selected = selected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = primaryColor,
                ),
            )
        }
    }
}

@Composable
private fun TraiReportSection(
    enabled: Boolean,
    isWithinComplaintWindow: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.BugReport,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Also report to TRAI",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Opens SMS to 1909 (TRAI helpline)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }

            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Text(
                        text = if (isWithinComplaintWindow)
                            "Within 7 days — TRAI will treat this as a Complaint and can take regulatory action against the sender."
                        else
                            "After 7 days — TRAI will record this as a Report (data only). No action is taken against the sender.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    )
                }
            }
        }
    }
}
