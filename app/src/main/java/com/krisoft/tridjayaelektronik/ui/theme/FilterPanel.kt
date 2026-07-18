package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * Kategori + Merk filter as one anchored dropdown panel (styled after the web dashboard's
 * filter popover) — shared by the Inventory list and Global Search so both filter the same
 * way. Free text is allowed (the DAO matches category/merk by substring) and matching options
 * pop up as autocomplete suggestions under the focused field. Draft state lives only while
 * the panel is open; commits on Terapkan, not per keystroke.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterPanelChip(
    category: String,
    merk: String,
    categories: List<String>,
    merks: List<String>,
    onApply: (category: String, merk: String) -> Unit,
    colors: SelectableChipColors
) {
    var expanded by remember { mutableStateOf(false) }
    val active = category.isNotEmpty() || merk.isNotEmpty()
    val chipLabel = listOf(category, merk).filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "Filter"

    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(chipLabel) },
            leadingIcon = { Icon(Icons.Rounded.FilterAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(16.dp)) },
            shape = RoundedCornerShape(50),
            colors = colors
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 6.dp
        ) {
            var categoryDraft by remember(expanded) { mutableStateOf(category) }
            var merkDraft by remember(expanded) { mutableStateOf(merk) }

            Column(modifier = Modifier.width(224.dp).padding(16.dp)) {
                FilterPanelField(
                    label = "Kategori",
                    value = categoryDraft,
                    onValueChange = { categoryDraft = it },
                    placeholder = "Cari kategori...",
                    options = categories
                )
                Spacer(modifier = Modifier.height(20.dp))
                FilterPanelField(
                    label = "Merk",
                    value = merkDraft,
                    onValueChange = { merkDraft = it },
                    placeholder = "Cari merk...",
                    options = merks
                )
                Spacer(modifier = Modifier.height(20.dp))
                ExpressiveFilledButton(
                    onClick = {
                        onApply(categoryDraft, merkDraft)
                        expanded = false
                    },
                    // Compact 40dp button: the default 16dp vertical padding clips the label.
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("Terapkan", style = MaterialTheme.typography.labelLarge)
                }
                if (active) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Reset filter",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onApply("", "")
                                expanded = false
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** Compact labelled search input (40dp, bordered) with inline autocomplete suggestions. */
@Composable
private fun FilterPanelField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    options: List<String>
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            }
        )
        // Autocomplete: matching options while typing, hidden once the text equals an option.
        val suggestions = remember(value, options) {
            if (value.isBlank()) emptyList()
            else options.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(4)
        }
        suggestions.forEach { option ->
            Text(
                text = option,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onValueChange(option) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
    }
}
