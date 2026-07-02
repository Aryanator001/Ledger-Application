package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.DatabaseStitcher
import com.example.data.LedgerScope
import com.example.data.UserProfile
import com.example.ui.components.buttons.PrimaryButton
import com.example.ui.components.settings.OptionPickerSheet
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import java.util.UUID

private val BUSINESS_TYPES = listOf(
    "Freelancer",
    "Sole Proprietor",
    "Partnership",
    "Private Limited (Pvt Ltd)",
    "LLP",
    "Other"
)

private val GST_REGEX = Regex("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")
private val PAN_REGEX = Regex("^[A-Z]{5}[0-9]{4}[A-Z]$")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSetupScreen(
    authViewModel: AuthViewModel,
    databaseStitcher: DatabaseStitcher,
    userProfileDao: com.example.data.UserProfileDao,
    ledgerScopeDao: com.example.data.LedgerScopeDao,
    userPreferences: com.example.data.UserPreferences,
    onSetupComplete: () -> Unit
) {
    val session by authViewModel.activeSession.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val appColors = MaterialTheme.appColors

    if (session == null) return

    // ── Personal fields (editable, pre-filled from session) ──────────
    var displayName by remember { mutableStateOf(session!!.displayName) }
    var email by remember { mutableStateOf(session!!.email) }
    var phoneNumber by remember { mutableStateOf("") }

    // ── Business fields ────────────────────────────────────────────────
    var businessName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }

    // ── Tax fields ─────────────────────────────────────────────────────
    var gstNumber by remember { mutableStateOf("") }
    var panNumber by remember { mutableStateOf("") }

    // ── UI state ────────────────────────────────────────────────────────
    var isSaving by remember { mutableStateOf(false) }
    var showBusinessTypePicker by remember { mutableStateOf(false) }

    val gstError = gstNumber.isNotBlank() && !GST_REGEX.matches(gstNumber.uppercase())
    val panError = panNumber.isNotBlank() && !PAN_REGEX.matches(panNumber.uppercase())

    val initials = remember(displayName) {
        displayName.trim().split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Set Up Your Profile", style = AppTypography.headline3)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = appColors.bgBase,
                    titleContentColor = appColors.textPrimary
                )
            )
        },
        containerColor = appColors.bgBase
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // ── Avatar hero ────────────────────────────────────────────
            Spacer(modifier = Modifier.height(AppSpacing.xl))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(appColors.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = AppTypography.headline2,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            Text(
                text = displayName.ifBlank { "Your Name" },
                style = AppTypography.headline3,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = email,
                style = AppTypography.bodySmall,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(AppSpacing.xl))

            // ── Section: Personal Information ──────────────────────────
            ProfileSection(title = "Personal Information") {
                ProfileFormField(
                    label = "Full Name",
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = "Your full name",
                    imeAction = ImeAction.Next
                )
                ProfileDivider()
                ProfileFormField(
                    label = "Email",
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "your@email.com",
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
                ProfileDivider()
                ProfileFormField(
                    label = "Phone Number",
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    placeholder = "+91 9876543210",
                    keyboardType = KeyboardType.Phone,
                    imeAction = ImeAction.Next
                )
            }

            // ── Section: Business Details ──────────────────────────────
            ProfileSection(title = "Business Details") {
                ProfileFormField(
                    label = "Business Name",
                    value = businessName,
                    onValueChange = { businessName = it },
                    placeholder = "Your business name",
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                )
                ProfileDivider()
                // Business type — tap to open picker
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBusinessTypePicker = true }
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Business Type",
                            style = AppTypography.bodySmall,
                            color = appColors.textTertiary
                        )
                        Text(
                            text = businessType.ifBlank { "Select type" },
                            style = AppTypography.bodyRegular,
                            color = if (businessType.isBlank()) appColors.textTertiary else appColors.textPrimary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = appColors.textTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                ProfileDivider()
                ProfileFormField(
                    label = "Business Address",
                    value = address,
                    onValueChange = { address = it },
                    placeholder = "Street, City, State, PIN",
                    maxLines = 3,
                    imeAction = ImeAction.Next
                )
                ProfileDivider()
                ProfileFormField(
                    label = "Website",
                    value = website,
                    onValueChange = { website = it },
                    placeholder = "https://yourbusiness.com",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                )
            }

            // ── Section: Tax Information ───────────────────────────────
            ProfileSection(title = "Tax Information") {
                ProfileFormField(
                    label = "GST Number",
                    value = gstNumber,
                    onValueChange = { gstNumber = it.uppercase() },
                    placeholder = "22AAAAA0000A1Z5",
                    capitalization = KeyboardCapitalization.Characters,
                    isError = gstError,
                    supportingText = if (gstError) "Invalid GST format (e.g. 22AAAAA0000A1Z5)" else null,
                    imeAction = ImeAction.Next
                )
                ProfileDivider()
                ProfileFormField(
                    label = "PAN Number",
                    value = panNumber,
                    onValueChange = { panNumber = it.uppercase() },
                    placeholder = "AAAAA0000A",
                    capitalization = KeyboardCapitalization.Characters,
                    isError = panError,
                    supportingText = if (panError) "Invalid PAN format (e.g. AAAAA0000A)" else null,
                    imeAction = ImeAction.Done
                )
            }

            // ── CTA ────────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = AppSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(
                    text = "Complete Setup",
                    isLoading = isSaving,
                    enabled = !isSaving && !gstError && !panError,
                    onClick = {
                        isSaving = true
                        coroutineScope.launch {
                            val uid = session!!.uid
                            val profile = UserProfile(
                                uid = uid,
                                displayName = displayName.trim().ifBlank { session!!.displayName },
                                email = email.trim().ifBlank { session!!.email },
                                photoUrl = session!!.photoUrl,
                                phoneNumber = phoneNumber.trim().takeIf { it.isNotBlank() },
                                businessName = businessName.trim().takeIf { it.isNotBlank() },
                                businessType = businessType.takeIf { it.isNotBlank() },
                                address = address.trim().takeIf { it.isNotBlank() },
                                website = website.trim().takeIf { it.isNotBlank() },
                                gstNumber = gstNumber.trim().takeIf { it.isNotBlank() },
                                panNumber = panNumber.trim().takeIf { it.isNotBlank() }
                            )
                            userProfileDao.insertProfile(profile)

                            val defaultScopeId = UUID.randomUUID().toString()
                            val scopeName = businessName.trim().ifBlank { "Personal Ledger" }
                            val scopeType = when (businessType) {
                                "Freelancer", "Sole Proprietor", "Partnership",
                                "Private Limited (Pvt Ltd)", "LLP" -> "BUSINESS"
                                else -> "PERSONAL"
                            }
                            ledgerScopeDao.insertScope(
                                LedgerScope(
                                    id = defaultScopeId,
                                    profileId = uid,
                                    name = scopeName,
                                    type = scopeType
                                )
                            )

                            databaseStitcher.stitchUnassignedData(uid, defaultScopeId)

                            userPreferences.setActiveUserId(uid)
                            userPreferences.setActiveLedgerScopeId(defaultScopeId)
                            userPreferences.setOnboardingCompleted(true)

                            isSaving = false
                            onSetupComplete()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                TextButton(
                    onClick = {
                        // Save minimal profile and skip to home
                        coroutineScope.launch {
                            val uid = session!!.uid
                            userProfileDao.insertProfile(
                                UserProfile(
                                    uid = uid,
                                    displayName = session!!.displayName,
                                    email = session!!.email,
                                    photoUrl = session!!.photoUrl
                                )
                            )
                            val scopeId = UUID.randomUUID().toString()
                            ledgerScopeDao.insertScope(
                                LedgerScope(id = scopeId, profileId = uid, name = "Personal Ledger", type = "PERSONAL")
                            )
                            databaseStitcher.stitchUnassignedData(uid, scopeId)
                            userPreferences.setActiveUserId(uid)
                            userPreferences.setActiveLedgerScopeId(scopeId)
                            userPreferences.setOnboardingCompleted(true)
                            onSetupComplete()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Skip for now",
                        style = AppTypography.bodyRegular,
                        color = appColors.textSecondary
                    )
                }
            }
        }
    }

    // ── Business type picker ───────────────────────────────────────────
    if (showBusinessTypePicker) {
        OptionPickerSheet(
            title = "Business Type",
            options = BUSINESS_TYPES,
            selectedOption = businessType,
            onOptionSelected = { businessType = it },
            onDismiss = { showBusinessTypePicker = false }
        )
    }
}

// ── Shared local composables ───────────────────────────────────────────

@Composable
private fun ProfileSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(top = AppSpacing.lg)) {
        Text(
            text = title.uppercase(),
            style = AppTypography.caption,
            color = MaterialTheme.appColors.textTertiary,
            modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.sm)
        )
        Card(
            shape = AppShapes.xl,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.appColors.bgSurface),
            modifier = Modifier
                .padding(horizontal = AppSpacing.lg)
                .fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ProfileDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = AppSpacing.lg),
        thickness = 0.5.dp,
        color = MaterialTheme.appColors.borderLight
    )
}

@Composable
private fun ProfileFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    maxLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null
) {
    val appColors = MaterialTheme.appColors
    Column(modifier = modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = {
                Text(
                    text = label,
                    style = AppTypography.bodySmall,
                )
            },
            placeholder = {
                Text(
                    text = placeholder,
                    style = AppTypography.bodyRegular,
                    color = appColors.textTertiary
                )
            },
            isError = isError,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = imeAction
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedTextColor = appColors.textPrimary,
                unfocusedTextColor = appColors.textPrimary,
                errorTextColor = appColors.textPrimary,
                cursorColor = appColors.primary,
                focusedLabelColor = appColors.primary,
                unfocusedLabelColor = appColors.textTertiary,
                errorLabelColor = appColors.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xs)
        )
        if (isError && supportingText != null) {
            Text(
                text = supportingText,
                style = AppTypography.caption,
                color = appColors.error,
                modifier = Modifier.padding(horizontal = AppSpacing.xl, vertical = 2.dp)
            )
        }
    }
}
