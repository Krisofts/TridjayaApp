package com.krisoft.tridjayaelektronik.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.R
import com.krisoft.tridjayaelektronik.ui.theme.ClayCard
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFormError
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.loginSuccess) {
        if (state.loginSuccess) onLoginSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Branded mascot header (like Rhythm's identity-forward screens).
        Surface(
            modifier = Modifier.size(96.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.mascot_greeting),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tridjaya",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Masuk dengan NIK atau WhatsApp",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ExpressiveTextField(
                    value = state.identifier,
                    onValueChange = viewModel::onIdentifierChange,
                    label = "NIK / WhatsApp",
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExpressiveTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = "Password",
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardType = KeyboardType.Password,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.errorMessage != null) {
                    ExpressiveFormError(message = state.errorMessage ?: "")
                }

                Spacer(modifier = Modifier.height(20.dp))

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
                        Text("Masuk")
                    }
                }

                TextButton(
                    onClick = onForgotPassword,
                    enabled = !state.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Lupa password?")
                }
            }
        }
    }
}
