package com.krisoft.tridjayaelektronik.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.krisoft.tridjayaelektronik.ui.login.ChangePasswordScreen
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveErrorState
import com.krisoft.tridjayaelektronik.ui.theme.Material3SettingsGroup
import com.krisoft.tridjayaelektronik.ui.theme.Material3SettingsItem
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader
import com.krisoft.tridjayaelektronik.ui.update.UpdateDialog
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.updateMessage) {
        state.updateMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeUpdateMessage()
        }
    }
    state.updateAvailable?.let { available ->
        UpdateDialog(
            available = available,
            onUpdate = {
                val target = available.updateUrl.ifBlank { "https://play.google.com/store/apps/details?id=${context.packageName}" }
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
            },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    // Logging out clears the session, which SessionViewModel observes reactively and routes
    // to Login for — no local "loggedOut" flag/callback needed here.

    var showTheme by remember { mutableStateOf(false) }
    if (showTheme) {
        ThemeSettingsScreen(onBack = { showTheme = false })
        return
    }

    var showChangePassword by remember { mutableStateOf(false) }
    if (showChangePassword) {
        ChangePasswordScreen(
            forced = false,
            onDone = { showChangePassword = false },
            onBack = { showChangePassword = false }
        )
        return
    }

    TridjayaCollapsibleHeader(title = "Pengaturan", onBack = onBack) { contentModifier ->
        Box(modifier = contentModifier) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                }
                state.user != null -> {
                    val user = state.user!!
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 100.dp)
                    ) {
                        ProfileHeader(name = user.name, role = user.role)

                        val infoItems = buildList {
                            add(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Person,
                                    title = { Text("Nama") },
                                    description = { Text(user.name) }
                                )
                            )
                            add(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Badge,
                                    title = { Text("NIK") },
                                    description = { Text(user.nik) }
                                )
                            )
                            add(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.WorkOutline,
                                    title = { Text("Role") },
                                    description = { Text(user.role) }
                                )
                            )
                            if (user.whatsapp.isNotBlank()) add(
                                Material3SettingsItem(
                                    icon = Icons.AutoMirrored.Rounded.Chat,
                                    title = { Text("WhatsApp") },
                                    description = { Text(user.whatsapp) }
                                )
                            )
                            if (user.email.isNotBlank()) add(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Email,
                                    title = { Text("Email") },
                                    description = { Text(user.email) }
                                )
                            )
                        }

                        Material3SettingsGroup(title = "Profil", items = infoItems)

                        Material3SettingsGroup(
                            title = "Tampilan",
                            items = listOf(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Palette,
                                    isHighlighted = true,
                                    title = { Text("Tema") },
                                    description = { Text("Warna, mode gelap, dynamic color") },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                    },
                                    onClick = { showTheme = true }
                                )
                            )
                        )

                        Material3SettingsGroup(
                            title = "Aplikasi",
                            items = listOf(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Info,
                                    title = { Text("Versi Aplikasi") },
                                    description = { Text("${viewModel.versionName} (${viewModel.versionCode})") }
                                ),
                                Material3SettingsItem(
                                    icon = Icons.Rounded.SystemUpdate,
                                    isHighlighted = true,
                                    title = { Text("Cek Pembaruan") },
                                    description = { Text(if (state.checkingUpdate) "Memeriksa…" else "Periksa versi terbaru") },
                                    trailingContent = {
                                        if (state.checkingUpdate) {
                                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                        }
                                    },
                                    onClick = { if (!state.checkingUpdate) viewModel.checkUpdate() }
                                )
                            )
                        )

                        Material3SettingsGroup(
                            title = "Akun",
                            items = listOf(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Lock,
                                    isHighlighted = true,
                                    title = { Text("Ganti Password") },
                                    description = { Text("Ubah password akunmu") },
                                    trailingContent = {
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                                    },
                                    onClick = { showChangePassword = true }
                                ),
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Logout,
                                    iconBackgroundTint = MaterialTheme.colorScheme.errorContainer,
                                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                    title = { Text("Keluar") },
                                    description = { Text("Akhiri sesi di perangkat ini") },
                                    onClick = viewModel::logout
                                )
                            )
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                state.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ExpressiveErrorState(
                            message = state.errorMessage ?: "Tidak bisa memuat profil.",
                            onRetry = viewModel::loadProfile,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Material3SettingsGroup(
                            items = listOf(
                                Material3SettingsItem(
                                    icon = Icons.Rounded.Logout,
                                    iconBackgroundTint = MaterialTheme.colorScheme.errorContainer,
                                    iconTint = MaterialTheme.colorScheme.onErrorContainer,
                                    title = { Text("Keluar") },
                                    onClick = viewModel::logout
                                )
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(name: String, role: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(44.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (role.isNotBlank()) {
            Text(
                text = role,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
