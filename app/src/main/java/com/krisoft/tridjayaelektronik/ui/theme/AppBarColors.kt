package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * App bar blends into the screen background instead of using Material3's default
 * surface-tinted app bar color, matching the flatter "expressive" reference look.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun tridjayaTopAppBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.background
)
