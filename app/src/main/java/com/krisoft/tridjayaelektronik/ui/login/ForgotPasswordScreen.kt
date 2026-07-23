package com.krisoft.tridjayaelektronik.ui.login

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

/**
 * Requests a password reset by identifier (email/NIK/phone). The server always answers 200 (no
 * account enumeration) and sends the reset link via WhatsApp, so on success we simply confirm and
 * offer to continue to the reset screen where the code goes.
 */
@Composable
fun ForgotPasswordScreen(
    onBack: () -> Unit,
    onHaveCode: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    TridjayaCollapsibleHeader(title = "Lupa Password", onBack = onBack) { contentModifier ->
        Column(
            modifier = contentModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text(
                text = "Masukkan email, NIK, atau nomor HP akunmu. Kami kirim tautan reset ke WhatsApp terdaftar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))

            ClayCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(20.dp)) {
                    if (state.sent) {
                        Text(
                            text = "Jika akun terdaftar, tautan reset sudah dikirim ke WhatsApp terdaftar.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        ExpressiveFilledButton(
                            onClick = onHaveCode,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Saya sudah punya kode") }
                    } else {
                        ExpressiveTextField(
                            value = state.identifier,
                            onValueChange = viewModel::onIdentifierChange,
                            label = "Email / NIK / No. HP",
                            keyboardType = KeyboardType.Text,
                            isError = state.errorMessage != null,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (state.errorMessage != null) {
                            ExpressiveFormError(message = state.errorMessage ?: "")
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
                                Text("Kirim Tautan Reset")
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onHaveCode, modifier = Modifier.fillMaxWidth()) {
                            Text("Sudah punya kode dari WhatsApp?")
                        }
                    }
                }
            }
        }
    }
}
