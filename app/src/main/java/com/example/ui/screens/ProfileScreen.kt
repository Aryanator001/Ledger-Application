package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.UserProfile
import com.example.data.UserProfileDao
import com.example.ui.components.settings.OptionPickerSheet
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppSpacing
import com.example.ui.theme.AppTypography
import com.example.ui.theme.appColors
import com.example.ui.viewmodel.AuthViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val PROFILE_BUSINESS_TYPES = listOf(
    "Freelancer",
    "Sole Proprietor",
    "Partnership",
    "Private Limited (Pvt Ltd)",
    "LLP",
    "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    userProfileDao: UserProfileDao,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = MaterialTheme.appColors

    val session by authViewModel.activeSession.collectAsStateWithLifecycle()

    val profileFlow = remember(session?.uid) {
        session?.uid?.let { userProfileDao.observeUserProfile(it) } ?: flowOf(null)
    }
    val profile by profileFlow.collectAsStateWithLifecycle(initialValue = null)

    var isEditing by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showBusinessTypePicker by remember { mutableStateOf(false) }

    var editName by remember { mutableStateOf("") }
    var editEmail by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editBusinessName by remember { mutableStateOf("") }
    var editBusinessType by remember { mutableStateOf("") }
    var editAddress by remember { mutableStateOf("") }
    var editWebsite by remember { mutableStateOf("") }
    var editGst by remember { mutableStateOf("") }
    var editPan by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        if (!isEditing) {
            editName = profile?.displayName ?: session?.displayName.orEmpty()
            editEmail = profile?.email ?: session?.email.orEmpty()
            editPhone = profile?.phoneNumber.orEmpty()
            editBusinessName = profile?.businessName.orEmpty()
            editBusinessType = profile?.businessType.orEmpty()
            editAddress = profile?.address.orEmpty()
            editWebsite = profile?.website.orEmpty()
            editGst = profile?.gstNumber.orEmpty()
            editPan = profile?.panNumber.orEmpty()
        }
    }

    val initials = remember(editName) {
        editName.trim().split(" ").take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("").ifEmpty { "?" }
    }

    fun resetEditFields() {
        editName = profile?.displayName ?: session?.displayName.orEmpty()
        editEmail = profile?.email ?: session?.email.orEmpty()
        editPhone = profile?.phoneNumber.orEmpty()
        editBusinessName = profile?.businessName.orEmpty()
        editBusinessType = profile?.businessType.orEmpty()
        editAddress = profile?.address.orEmpty()
        editWebsite = profile?.website.orEmpty()
        editGst = profile?.gstNumber.orEmpty()
        editPan = profile?.panNumber.orEmpty()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(stringResource(R.string.profile_screen_title), style = AppTypography.headline3)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = appColors.textPrimary
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(onClick = {
                            resetEditFields()
                            isEditing = false
                        }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = appColors.textPrimary)
                        }
                        IconButton(
                            onClick = {
                                isSaving = true
                                scope.launch {
                                    val uid = session?.uid ?: return@launch
                                    userProfileDao.insertProfile(
                                        UserProfile(
                                            uid = uid,
                                            displayName = editName.trim().ifBlank { session?.displayName.orEmpty() },
                                            email = editEmail.trim().ifBlank { session?.email.orEmpty() },
                                            photoUrl = profile?.photoUrl ?: session?.photoUrl,
                                            phoneNumber = editPhone.trim().takeIf { it.isNotBlank() },
                                            businessName = editBusinessName.trim().takeIf { it.isNotBlank() },
                                            businessType = editBusinessType.takeIf { it.isNotBlank() },
                                            address = editAddress.trim().takeIf { it.isNotBlank() },
                                            website = editWebsite.trim().takeIf { it.isNotBlank() },
                                            gstNumber = editGst.trim().takeIf { it.isNotBlank() },
                                            panNumber = editPan.trim().takeIf { it.isNotBlank() }
                                        )
                                    )
                                    isSaving = false
                                    isEditing = false
                                }
                            },
                            enabled = !isSaving
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = appColors.primary
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = null, tint = appColors.primary)
                            }
                        }
                    } else {
                        IconButton(onClick = { isEditing = true }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = appColors.textPrimary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = appColors.bgBase,
                    titleContentColor = appColors.textPrimary
                )
            )
        },
        containerColor = appColors.bgBase
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(bottom = AppSpacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Avatar hero
            Spacer(modifier = Modifier.height(AppSpacing.xl))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(appColors.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(text = initials, style = AppTypography.headline2, color = Color.White)
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            Text(
                text = editName.ifBlank { stringResource(R.string.settings_profile_name_placeholder) },
                style = AppTypography.headline3,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = AppSpacing.xl)
            )
            Text(
                text = editEmail,
                style = AppTypography.bodySmall,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )

            Spacer(modifier = Modifier.height(AppSpacing.xl))

            // Personal Information
            ProfileViewSection(title = stringResource(R.string.profile_section_personal)) {
                if (isEditing) {
                    ProfileEditField(
                        label = stringResource(R.string.profile_full_name),
                        value = editName,
                        onValueChange = { editName = it },
                        imeAction = ImeAction.Next
                    )
                    ProfileItemDivider()
                    ProfileEditField(
                        label = stringResource(R.string.profile_email),
                        value = editEmail,
                        onValueChange = { editEmail = it },
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                    ProfileItemDivider()
                    ProfileEditField(
                        label = stringResource(R.string.profile_phone),
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Next
                    )
                } else {
                    ProfileViewField(label = stringResource(R.string.profile_full_name), value = editName)
                    ProfileItemDivider()
                    ProfileViewField(label = stringResource(R.string.profile_email), value = editEmail)
                    ProfileItemDivider()
                    ProfileViewField(label = stringResource(R.string.profile_phone), value = editPhone)
                }
            }

            // Business Details
            ProfileViewSection(title = stringResource(R.string.profile_section_business)) {
                if (isEditing) {
                    ProfileEditField(
                        label = stringResource(R.string.profile_business_name),
                        value = editBusinessName,
                        onValueChange = { editBusinessName = it },
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    )
                    ProfileItemDivider()
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
                                text = stringResource(R.string.profile_business_type),
                                style = AppTypography.bodySmall,
                                color = appColors.textTertiary
                            )
                            Text(
                                text = editBusinessType.ifBlank { stringResource(R.string.profile_not_set) },
                                style = AppTypography.bodyRegular,
                                color = if (editBusinessType.isBlank()) appColors.textTertiary else appColors.textPrimary,
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
                    ProfileItemDivider()
                    ProfileEditField(
                        label = stringResource(R.string.profile_business_address),
                        value = editAddress,
                        onValueChange = { editAddress = it },
                        maxLines = 3,
                        imeAction = ImeAction.Next
                    )
                    ProfileItemDivider()
                    ProfileEditField(
                        label = stringResource(R.string.profile_website),
                        value = editWebsite,
                        onValueChange = { editWebsite = it },
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next
                    )
                } else {
                    ProfileViewField(label = stringResource(R.string.profile_business_name), value = editBusinessName)
                    ProfileItemDivider()
                    ProfileViewField(label = stringResource(R.string.profile_business_type), value = editBusinessType)
                    ProfileItemDivider()
                    ProfileViewField(label = stringResource(R.string.profile_business_address), value = editAddress)
                    ProfileItemDivider()
                    ProfileViewField(label = stringResource(R.string.profile_website), value = editWebsite)
                }
            }

            // Tax Information
            ProfileViewSection(title = stringResource(R.string.profile_section_tax)) {
                if (isEditing) {
                    ProfileEditField(
                        label = stringResource(R.string.profile_gst_number),
                        value = editGst,
                        onValueChange = { editGst = it.uppercase() },
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Next
                    )
                    ProfileItemDivider()
                    ProfileEditField(
                        label = stringResource(R.string.profile_pan_number),
                        value = editPan,
                        onValueChange = { editPan = it.uppercase() },
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    )
                } else {
                    ProfileViewField(label = stringResource(R.string.profile_gst_number), value = editGst)
                    ProfileItemDivider()
                    ProfileViewField(label = stringResource(R.string.profile_pan_number), value = editPan)
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xl))

            // Sign Out
            Button(
                onClick = { showSignOutDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = appColors.error),
                shape = AppShapes.xl,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg)
                    .height(AppSpacing.buttonHeight)
            ) {
                Text(
                    text = stringResource(R.string.profile_sign_out),
                    style = AppTypography.bodyLarge,
                    color = Color.White
                )
            }
        }

        if (showSignOutDialog) {
            AlertDialog(
                onDismissRequest = { showSignOutDialog = false },
                title = {
                    Text(stringResource(R.string.profile_sign_out_title), style = AppTypography.headline3)
                },
                text = {
                    Text(stringResource(R.string.profile_sign_out_message), style = AppTypography.bodyRegular)
                },
                confirmButton = {
                    TextButton(onClick = {
                        showSignOutDialog = false
                        authViewModel.signOut(context)
                    }) {
                        Text(
                            stringResource(R.string.profile_sign_out_confirm),
                            color = appColors.error
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSignOutDialog = false }) {
                        Text(stringResource(R.string.profile_cancel))
                    }
                },
                containerColor = appColors.bgSurface,
                titleContentColor = appColors.textPrimary,
                textContentColor = appColors.textSecondary
            )
        }

        if (showBusinessTypePicker) {
            OptionPickerSheet(
                title = stringResource(R.string.profile_business_type),
                options = PROFILE_BUSINESS_TYPES,
                selectedOption = editBusinessType,
                onOptionSelected = { editBusinessType = it },
                onDismiss = { showBusinessTypePicker = false }
            )
        }
    }
}

@Composable
private fun ProfileViewSection(
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
private fun ProfileItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = AppSpacing.lg),
        thickness = 0.5.dp,
        color = MaterialTheme.appColors.borderLight
    )
}

@Composable
private fun ProfileViewField(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
    ) {
        Text(text = label, style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
        Text(
            text = value.ifBlank { stringResource(R.string.profile_not_set) },
            style = AppTypography.bodyRegular,
            color = if (value.isBlank()) MaterialTheme.appColors.textDisabled else MaterialTheme.appColors.textPrimary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ProfileEditField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    imeAction: ImeAction = ImeAction.Next,
    maxLines: Int = 1
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm)
    ) {
        Text(text = label, style = AppTypography.caption, color = MaterialTheme.appColors.textTertiary)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = AppTypography.bodyRegular,
            maxLines = maxLines,
            singleLine = maxLines == 1,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                imeAction = imeAction
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.appColors.textPrimary,
                unfocusedTextColor = MaterialTheme.appColors.textPrimary,
                focusedBorderColor = MaterialTheme.appColors.primary,
                unfocusedBorderColor = MaterialTheme.appColors.borderLight,
                cursorColor = MaterialTheme.appColors.primary
            )
        )
    }
}
