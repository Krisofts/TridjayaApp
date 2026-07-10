package com.krisoft.tridjayaelektronik.ui.leads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.krisoft.tridjayaelektronik.data.model.PipelineDto
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextField
import com.krisoft.tridjayaelektronik.ui.theme.TridjayaCollapsibleHeader

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddLeadScreen(
    onBack: () -> Unit,
    onLeadCreated: () -> Unit,
    viewModel: AddLeadViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(state.createdLeadId) {
        if (state.createdLeadId != null) onLeadCreated()
    }

    TridjayaCollapsibleHeader(title = "Tambah Prospek", onBack = onBack) { contentModifier ->
        Column(
            modifier = contentModifier
                // imePadding shrinks the scroll viewport by the keyboard height, so a focused field
                // (e.g. Catatan at the bottom) auto-scrolls above the keyboard instead of hiding under it.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navBarInset + 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FormSection(title = "Kontak") {
                ExpressiveTextField(
                    value = state.nama,
                    onValueChange = viewModel::onNamaChange,
                    label = "Nama",
                    placeholder = "Nama lengkap prospek",
                    modifier = Modifier.fillMaxWidth()
                )
                ExpressiveTextField(
                    value = state.phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = "Nomor WhatsApp",
                    placeholder = "08xxxxxxxxxx",
                    keyboardType = KeyboardType.Phone,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            FormSection(title = "Peluang") {
                ExpressiveTextField(
                    value = state.estimatedValue,
                    onValueChange = viewModel::onEstimatedValueChange,
                    label = "Estimasi Nilai (Rp)",
                    placeholder = "0",
                    keyboardType = KeyboardType.Number,
                    supportingText = previewRupiah(state.estimatedValue),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.isLoadingPipelines) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else if (state.pipelines.isNotEmpty()) {
                    Text(
                        text = "Pipeline",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.pipelines.forEach { pipeline: PipelineDto ->
                            FilterChip(
                                selected = state.selectedPipelineId == pipeline.id,
                                onClick = { viewModel.onPipelineSelected(pipeline.id) },
                                label = { Text(pipeline.nama) },
                                shape = RoundedCornerShape(50),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }

            FormSection(title = "Detail") {
                ExpressiveTextField(
                    value = state.sumber,
                    onValueChange = viewModel::onSumberChange,
                    label = "Sumber",
                    placeholder = "showroom / WA / referral",
                    modifier = Modifier.fillMaxWidth()
                )
                ExpressiveTextField(
                    value = state.lokasi,
                    onValueChange = viewModel::onLokasiChange,
                    label = "Lokasi",
                    modifier = Modifier.fillMaxWidth()
                )
                ExpressiveTextField(
                    value = state.catatan,
                    onValueChange = viewModel::onCatatanChange,
                    label = "Catatan",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (state.errorMessage != null) {
                Text(text = state.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
            }

            ExpressiveFilledButton(
                onClick = viewModel::submit,
                enabled = !state.isSubmitting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Simpan Prospek")
                }
            }
        }
    }
}

/** A titled card grouping related fields — gives the form a clean, sectioned CRM structure. */
@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            content()
        }
    }
}

/** Live "Rp 5.000.000" preview under the amount field; null (no supporting text) when empty. */
private fun previewRupiah(digits: String): String? {
    if (digits.isBlank()) return null
    val n = digits.toLongOrNull() ?: return null
    if (n <= 0) return null
    val grouped = n.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $grouped"
}
