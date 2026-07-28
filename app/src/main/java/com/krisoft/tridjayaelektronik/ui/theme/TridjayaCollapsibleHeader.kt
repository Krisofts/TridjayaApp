package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Static (non-collapsing) large-title header. Was a scroll-collapsing [LargeTopAppBar]
 * (Rhythm-style, title shrinking 32sp→24sp as content scrolled) — removed at the user's request
 * so every screen using this component gets a fixed-height app bar that never collapses/shrinks
 * on scroll. Keeps the same external API (title/onBack/actions/content slot) so no call site
 * anywhere in the app needed to change. Content still fades + slides up on first show, matching
 * Rhythm's entrance animation (alpha over 400ms, 30px rise over 450ms after a 50ms delay).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TridjayaCollapsibleHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    // Entrance animation — content fades in and rises slightly on first composition.
    var showContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(50)
        showContent = true
    }
    val contentAlpha by animateFloatAsState(
        targetValue = if (showContent) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "content_alpha"
    )
    val contentOffset by animateFloatAsState(
        targetValue = if (showContent) 0f else 30f,
        animationSpec = tween(durationMillis = 450),
        label = "content_offset"
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                Spacer(modifier = Modifier.height(10.dp))
                TopAppBar(
                    title = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.padding(start = 12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                        contentDescription = "Kembali",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 10.dp),
                            content = actions
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        }
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                // Edge-to-edge (decorFitsSystemWindows=false) → window tak resize saat keyboard
                // muncul; tanpa imePadding, field di bawah tertutup keyboard. imePadding=0 saat
                // keyboard tertutup (tak berefek), menyusut ke atas keyboard saat terbuka →
                // field ter-fokus otomatis di-scroll ke area terlihat. Fix untuk SEMUA form yang
                // pakai header ini.
                .imePadding()
                .graphicsLayer {
                    alpha = contentAlpha
                    translationY = contentOffset
                }
        )
    }
}
