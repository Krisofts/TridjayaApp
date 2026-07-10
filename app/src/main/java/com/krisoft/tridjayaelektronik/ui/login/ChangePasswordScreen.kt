package com.krisoft.tridjayaelektronik.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Change-password form. In [forced] mode (server flagged `must_change_password`) the header has no
 * back button and system back is swallowed — the user cannot proceed into the app until they change
 * it. In voluntary mode (from Settings) it behaves like a normal pushed screen with a back button.
 */
@Composable
fun ChangePasswordScreen(
    forced: Boolean,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChangePasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.success) { if (state.success) onDone() }
    // In forced mode, block back so the gate can't be skipped.
    BackHandler(enabled = forced) { /* swallowed */ }

    TridjayaCollapsibleHeader(
        title = "Ganti Password",
        onBack = if (forced) null else onBack
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            if (forced) {
                Text(
                    text = "Demi keamanan, buat password baru dulu sebelum melanjutkan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
            }

            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(Modifier.padding(20.dp)) {
                    ExpressiveTextField(
                        value = state.oldPassword,
                        onValueChange = viewModel::onOldChange,
                        label = "Password lama",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    ExpressiveTextField(
                        value = state.newPassword,
                        onValueChange = viewModel::onNewChange,
                        label = "Password baru",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    ExpressiveTextField(
                        value = state.confirmPassword,
                        onValueChange = viewModel::onConfirmChange,
                        label = "Ulangi password baru",
                        keyboardType = KeyboardType.Password,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = state.errorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.errorMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(Modifier.height(20.dp))
                    ExpressiveFilledButton(
                        onClick = viewModel::submit,
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text("Simpan Password")
                        }
                    }
                }
            }
        }
    }
}
