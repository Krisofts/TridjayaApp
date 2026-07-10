package com.krisoft.tridjayaelektronik.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.krisoft.tridjayaelektronik.ui.theme.ExpressiveTextButton

/**
 * "Atur Tampilan Home" — reorder (up/down) and show/hide dashboard sections.
 * Tridjaya's equivalent of Rhythm's Home section-order bottom sheet, using up/down
 * arrows instead of drag handles (no extra reorder dependency).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCustomizeSheet(
    layout: HomeLayout,
    onMoveUp: (HomeSection) -> Unit,
    onMoveDown: (HomeSection) -> Unit,
    onToggle: (HomeSection, Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Atur Tampilan Home",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Urutkan & tampilkan section dashboard",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ExpressiveTextButton(onClick = onReset) { Text("Reset") }
            }

            Spacer(Modifier.height(12.dp))

            layout.order.forEachIndexed { index, section ->
                SectionRow(
                    section = section,
                    visible = layout.isVisible(section),
                    canMoveUp = index > 0,
                    canMoveDown = index < layout.order.lastIndex,
                    onMoveUp = { onMoveUp(section) },
                    onMoveDown = { onMoveDown(section) },
                    onToggle = { onToggle(section, it) }
                )
                if (index < layout.order.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionRow(
    section: HomeSection,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (visible) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.weight(1f)
            )

            ReorderArrow(
                enabled = canMoveUp,
                onClick = onMoveUp,
                content = { Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Naikkan ${section.label}") }
            )
            ReorderArrow(
                enabled = canMoveDown,
                onClick = onMoveDown,
                content = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Turunkan ${section.label}") }
            )
            Spacer(Modifier.size(4.dp))
            Switch(checked = visible, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun ReorderArrow(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}
