package com.krisoft.tridjayaelektronik.ui.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.krisoft.tridjayaelektronik.data.update.UpdateStatus
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveFilledButton
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveShapes
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton

/**
 * Update prompt. When [available] is a force update the dialog can't be dismissed (no cancel button,
 * back/outside taps ignored) — the user must update to continue. Optional updates offer "Nanti".
 */
@Composable
fun UpdateDialog(
    available: UpdateStatus.Available,
    onUpdate: () -> Unit,
    onDismiss: (() -> Unit)?
) {
    val force = available.force
    AlertDialog(
        onDismissRequest = { onDismiss?.invoke() },
        shape = ExpressiveShapes.Large,
        icon = { Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                text = if (force) "Pembaruan Wajib" else "Pembaruan Tersedia",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text("Versi ${available.latestVersionName} sudah tersedia.")
                if (available.releaseNotes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = available.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (force) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Perbarui aplikasi untuk melanjutkan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            ExpressiveFilledButton(onClick = onUpdate) { Text("Perbarui Sekarang") }
        },
        dismissButton = if (force || onDismiss == null) null else {
            { ExpressiveTextButton(onClick = onDismiss) { Text("Nanti") } }
        },
        properties = DialogProperties(
            dismissOnBackPress = !force,
            dismissOnClickOutside = !force
        )
    )
}
