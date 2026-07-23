package com.krisoft.tridjayaelektronik.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Shared "Expressive" component set ported from the Rhythm reference app, so every screen in
 * this app draws its buttons/fields/cards from the same shape+animation language instead of
 * ad-hoc stock Material3 styling per screen.
 */
object ExpressiveShapes {
    val Full = CircleShape
    // Radius scale aligned 1:1 to the web dashboard's Tailwind radius tokens
    // (xs/sm/md/lg/xl = 4/8/12/16/24dp) — every tier shifted down one step from the old values
    // (was 8/12/16/24/28dp) so the name-to-dp mapping matches the web token names exactly.
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Large = RoundedCornerShape(16.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Small = RoundedCornerShape(8.dp)
    val ExtraSmall = RoundedCornerShape(4.dp)

    // Expressive asymmetric tokens (Rhythm's ExpressiveShapeTokens): a squircle with one sharp-ish
    // corner for icon badges/heroes, and top-rounded sheet shapes.
    val Squircle = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 12.dp, bottomEnd = 32.dp)
    val SquircleSmall = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 20.dp)
    val BottomSheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp)
}

/** Tracks a [MutableInteractionSource]'s press state so buttons can bounce on press like Rhythm's. */
@Composable
private fun rememberPressState(source: MutableInteractionSource): Boolean {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(source) {
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> pressed = true
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }
    return pressed
}

/** Pill-shaped primary button with Rhythm's press-scale bounce. */
@Composable
fun ExpressiveFilledButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = rememberPressState(interactionSource)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "filled_button_scale"
    )
    Button(
        onClick = rememberHapticClick(onClick),
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = shape,
        colors = colors,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/** Pill-shaped outlined (secondary) button, same press-scale treatment. */
@Composable
fun ExpressiveOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ExpressiveShapes.Full,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = rememberPressState(interactionSource)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "outlined_button_scale"
    )
    OutlinedButton(
        onClick = rememberHapticClick(onClick),
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        shape = shape,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        content = content
    )
}

/** Text-only tertiary action, kept flat (no scale) matching Rhythm's `ExpressiveTextButton`. */
@Composable
fun ExpressiveTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    TextButton(
        onClick = rememberHapticClick(onClick),
        modifier = modifier,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        content = content
    )
}

/** Circular filled icon button with Rhythm's tighter press-scale (icon buttons bounce more). */
@Composable
fun ExpressiveFilledIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.filledIconButtonColors(),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = rememberPressState(interactionSource)
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "filled_icon_button_scale"
    )
    IconButton(
        onClick = rememberHapticClick(onClick),
        modifier = modifier.size(size).graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content
    )
}

/** Outlined text field with Rhythm's rounded-medium shape — the one override the reference app applies. */
@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        isError = isError,
        supportingText = supportingText?.let {
            { Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        singleLine = singleLine,
        shape = ExpressiveShapes.Medium,
        // Border eksplisit: theme `outline` nyaris senada background kartu
        // (light E4E7EC di atas kartu terang; dark = persis surfaceVariant) →
        // field terlihat menyatu tanpa batas. onSurfaceVariant kontras di kedua tema.
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            disabledBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon
    )
}

/** Squircle-large empty-state card: icon badge + title + subtitle, matching Rhythm's `ModernEmptyState`. */
@Composable
fun ExpressiveEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = ExpressiveShapes.Squircle,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Box(modifier = Modifier.padding(18.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Error state with a retry affordance — the consistent "no connection / load failed" treatment used
 * across data screens (Home, Inventory, Leads, …). Same card shape as [ExpressiveEmptyState] but with
 * an error-tinted icon badge and a "Coba lagi" button.
 */
@Composable
fun ExpressiveErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Gagal memuat",
    retryLabel: String = "Coba lagi",
    icon: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
    }
) {
    ElevatedCard(
        modifier = modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(32.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = ExpressiveShapes.Squircle,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ) {
                Box(modifier = Modifier.padding(18.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Spacer(modifier = Modifier.size(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.size(20.dp))
            ExpressiveFilledButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(retryLabel)
            }
        }
    }
}

/**
 * Inline error row for when content is still visible but a background action failed (e.g. a sync
 * that didn't block already-cached data, or a paging append that failed mid-scroll) — the lighter
 * counterpart to [ExpressiveErrorState], which replaces the whole screen. One shared look for every
 * "something failed but you can keep going" case instead of a bespoke banner per screen.
 */
@Composable
fun ExpressiveInlineError(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Coba lagi"
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = ExpressiveShapes.Medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry) { Text(retryLabel) }
            }
        }
    }
}

/**
 * Small error text for form/login validation failures — the consistent "this submission failed"
 * message under a form's fields/button, replacing the per-screen mix of spacer gaps and typography.
 */
@Composable
fun ExpressiveFormError(message: String, modifier: Modifier = Modifier) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = modifier.padding(top = 12.dp)
    )
}

/** Section title row (bold title + optional subtitle + trailing action), matching Rhythm's `ModernSectionTitle`. */
@Composable
fun ExpressiveSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        trailing?.invoke()
    }
}
