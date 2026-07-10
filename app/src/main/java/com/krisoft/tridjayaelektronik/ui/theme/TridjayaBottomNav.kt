package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Rhythm's actual bottom navigation (as seen on its Home screen): a pill-shaped
 * [FloatingNavigationBar] floating at the bottom-start holding the browse tabs, plus a
 * separate circular search button at the bottom-end. Ported 1:1 from Rhythm's
 * `FloatingNavigationBar` / `FloatingNavigationBarItem`
 * (github.com/cromaguy/Rhythm, `shared/presentation/components/common/ExpressiveNavigation.kt`).
 *
 * Tridjaya mapping: the pill carries [pillItems] (Home, Prospek) and the search FAB carries
 * [searchItem] (Cari) — Cari is the search/inventory tab, matching Rhythm's search FAB exactly.
 */
@Composable
fun TridjayaFloatingNav(
    pillItems: List<TridjayaNavItem>,
    searchItem: TridjayaNavItem,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pill stretches to fill the row up to the search FAB; its items spread evenly.
        FloatingNavigationBar(modifier = Modifier.weight(1f)) {
            pillItems.forEach { item ->
                FloatingNavigationBarItem(
                    modifier = Modifier.weight(1f),
                    selected = item.selected,
                    onClick = item.onClick,
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    label = item.label
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        SearchNavButton(item = searchItem)
    }
}

data class TridjayaNavItem(
    val icon: ImageVector,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

/** Circular search FAB mirroring Rhythm's home-screen search button. */
@Composable
private fun SearchNavButton(
    item: TridjayaNavItem,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "search_fab_scale"
    )

    // Rhythm's search FAB is always a bold, dark, saturated circle — never a washed-out
    // neutral. Keep it solid `primary` in every state; press just deepens it slightly.
    val container by animateColorAsState(
        targetValue = if (isPressed) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "search_fab_container"
    )
    val content = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier = modifier
            .size(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = rememberHapticClick(item.onClick),
        shape = CircleShape,
        color = container,
        contentColor = content,
        // No drop shadow — Rhythm's floating nav is flat (tonal tint only).
        shadowElevation = 0.dp,
        tonalElevation = 4.dp,
        interactionSource = interactionSource
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

// ============================================================================
// FLOATING NAVIGATION BAR — ported 1:1 from Rhythm
// ============================================================================

/**
 * Pill-shaped floating navigation container.
 */
@Composable
fun FloatingNavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        // No drop shadow — Rhythm's floating nav pill is flat (tonal tint only).
        shadowElevation = 0.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Floating navigation item: transparent when idle (icon only), fills with a
 * primary-container pill and reveals its label when selected.
 */
@Composable
fun FloatingNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    selectedIcon: @Composable () -> Unit = icon,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
) {
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.88f
            selected -> 1f
            else -> 0.92f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "floating_nav_item_scale"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "container_color"
    )

    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "content_color"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        onClick = rememberHapticClick(onClick),
        enabled = enabled,
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (selected && label != null) 16.dp else 12.dp,
                vertical = 12.dp
            ),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp)) {
                if (selected) {
                    selectedIcon()
                } else {
                    icon()
                }
            }

            AnimatedVisibility(
                visible = selected && label != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = label ?: "",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
