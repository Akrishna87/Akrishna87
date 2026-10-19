package io.github.akrishna87.lumen.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Plain clickable with no ripple: iOS controls dim instead of rippling. */
@Composable
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)

/**
 * The 44dp navigation bar under the status bar: back chevron with label, centred title (and
 * optional subtitle, like the viewer's date and time), trailing buttons.
 */
@Composable
fun NavBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showTitle: Boolean = true,
    showBackground: Boolean = true,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val apple = LocalApple.current
    val background by animateColorAsState(if (showBackground) apple.bar else apple.bar.copy(alpha = 0f), label = "navBar")
    Column(modifier.fillMaxWidth().background(background).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            if (onBack != null) {
                Row(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(0.3f).height(44.dp).tap(onClick = onBack).padding(start = 6.dp)
                        .semantics { contentDescription = "Back"; role = Role.Button },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.ChevronLeft, null, tint = apple.tint, modifier = Modifier.size(26.dp))
                    if (backLabel != null) {
                        Text(backLabel, style = AppleType.body, color = apple.tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // The small title fades in once the large title has scrolled away.
            val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, label = "navTitle")
            Column(
                Modifier.align(Alignment.Center).fillMaxWidth(0.46f).alpha(titleAlpha),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = if (subtitle != null) AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold) else AppleType.headline,
                    color = apple.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (subtitle != null) {
                    Text(subtitle, style = AppleType.caption1, color = apple.secondaryLabel, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = trailing,
            )
        }
        Hairline(Modifier.alpha(if (showBackground) 1f else 0f))
    }
}

/** A large title, as the first item of a scrolling screen. */
@Composable
fun LargeTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)) {
        Text(title, style = AppleType.largeTitle, color = apple.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (subtitle != null) Text(subtitle, style = AppleType.subheadline, color = apple.secondaryLabel)
    }
}

/** A tinted, borderless icon button sized like a UIBarButtonItem. */
@Composable
fun BarIcon(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color? = null, size: Dp = 24.dp, onClick: () -> Unit) {
    val apple = LocalApple.current
    Box(
        Modifier.size(44.dp).clip(CircleShape)
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 22.dp), onClick = onClick)
            .semantics { contentDescription = description; role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = (tint ?: apple.tint).copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(size))
    }
}

/** Tinted text button ("Done", "Cancel", "Select All"). */
@Composable
fun BarText(text: String, bold: Boolean = false, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    val apple = LocalApple.current
    Text(
        text,
        style = AppleType.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        color = (color ?: apple.tint).copy(alpha = if (enabled) 1f else 0.3f),
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).tap(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

/**
 * The capsule button Photos floats over the grid ("Select", "Cancel"). Over photos it's a dark
 * translucent pill with white text; on a plain background it's a grey pill with tinted text.
 */
@Composable
fun CapsuleButton(text: String, overMedia: Boolean, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    val apple = LocalApple.current
    val bg = when {
        highlighted -> apple.tint
        overMedia -> Color.Black.copy(alpha = 0.32f)
        else -> apple.fill
    }
    val fg = when {
        highlighted || overMedia -> Color.White
        else -> apple.tint
    }
    Text(
        text,
        style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
        color = fg,
        maxLines = 1,
        modifier = modifier.clip(CircleShape).background(bg).tap(onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 0.dp) {
    Box(modifier.padding(start = start).fillMaxWidth().height(0.5.dp).background(LocalApple.current.separator))
}

/** A section heading on a plain background, like "My Albums" with its "See All" link. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth()) {
        Hairline(start = 16.dp)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = AppleType.title2, color = apple.label, modifier = Modifier.weight(1f))
            if (action != null) BarText(action, onClick = onAction)
        }
    }
}

/** Inset-grouped section (rounded card on the grouped background), with optional header and footer. */
@Composable
fun GroupedSection(
    header: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = if (header != null) 22.dp else 14.dp)) {
        if (header != null) {
            Text(header.uppercase(), style = AppleType.footnote, color = apple.secondaryLabel, modifier = Modifier.padding(start = 16.dp, bottom = 7.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(apple.cell), content = content)
        if (footer != null) {
            Text(footer, style = AppleType.footnote, color = apple.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** A list row: leading icon, title, trailing value and chevron, with an inset separator. */
@Composable
fun AppleRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    chevron: Boolean = true,
    titleColor: Color? = null,
    showDivider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val apple = LocalApple.current
    val hasLeading = icon != null || leading != null
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> { leading(); Spacer(Modifier.width(12.dp)) }
                icon != null -> {
                    Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                        Icon(icon, null, tint = apple.tint, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                }
            }
            Text(title, style = AppleType.body, color = titleColor ?: apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (value != null) Text(value, style = AppleType.body, color = apple.secondaryLabel, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
            if (chevron) {
                Spacer(Modifier.width(6.dp))
                Icon(AppIcons.ChevronRight, null, tint = apple.tertiaryLabel, modifier = Modifier.size(18.dp))
            }
        }
        if (showDivider) Hairline(start = if (hasLeading) 56.dp else 16.dp)
    }
}

/** UISegmentedControl: a grey track with the chosen segment raised in white. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, track: Color? = null) {
    val apple = LocalApple.current
    BoxWithConstraints(modifier.height(32.dp).clip(RoundedCornerShape(9.dp)).background(track ?: apple.fill).padding(2.dp)) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, label = "segment")
        Box(
            Modifier.offset(x = x).width(segment).fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(7.dp), clip = false)
                .clip(RoundedCornerShape(7.dp)).background(apple.selectedSegment),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().tap { onSelect(i) }
                        .semantics { this.selected = i == selected; role = Role.Tab },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = AppleType.footnote.copy(fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = apple.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

data class TabItem(val label: String, val icon: ImageVector)

/** UITabBar: translucent, hairline on top, 49dp tall above the gesture area. */
@Composable
fun TabBar(tabs: List<TabItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth().background(apple.bar)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(49.dp)) {
            tabs.forEachIndexed { i, tab ->
                val color = if (i == selected) apple.tint else apple.gray
                Column(
                    Modifier.weight(1f).fillMaxHeight().tap { onSelect(i) }
                        .semantics(mergeDescendants = true) { this.selected = i == selected; role = Role.Tab },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(tab.icon, null, tint = color, modifier = Modifier.size(27.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(tab.label, style = AppleType.tabLabel, color = color)
                }
            }
        }
    }
}

/** UISearchBar: grey rounded field with a magnifier, a clear button, and Cancel while editing. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    val apple = LocalApple.current
    val focus = LocalFocusManager.current
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).height(36.dp).clip(RoundedCornerShape(10.dp)).background(apple.fill).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(AppIcons.SearchSmall, null, tint = apple.secondaryLabel, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(placeholder, style = AppleType.body, color = apple.secondaryLabel, maxLines = 1)
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = AppleType.body.copy(color = apple.label),
                    cursorBrush = SolidColor(apple.tint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).onFocusChanged { onFocusChange(it.isFocused) }
                        .semantics { contentDescription = placeholder },
                )
            }
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(apple.secondaryLabel.copy(alpha = 0.45f)).tap { onQueryChange("") }
                        .semantics { contentDescription = "Clear text"; role = Role.Button },
                    contentAlignment = Alignment.Center,
                ) { Icon(AppIcons.Close, null, tint = apple.cell, modifier = Modifier.size(11.dp)) }
            }
        }
        if (focused) {
            BarText("Cancel") {
                onQueryChange("")
                focus.clearFocus()
            }
        }
    }
}

data class SheetAction(val label: String, val destructive: Boolean = false, val onClick: () -> Unit)

/**
 * UIAlertController's action sheet: a message and actions in a rounded card at the bottom, with a
 * separate bold Cancel below. Photos uses it to confirm deleting.
 */
@Composable
fun ActionSheet(message: String?, actions: List<SheetAction>, onDismiss: () -> Unit) {
    val apple = LocalApple.current
    val card = if (apple.dark) Color(0xFF2C2C2E) else Color(0xF2FFFFFF)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().tap(onClick = onDismiss), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp).tap {}) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(card)) {
                    if (message != null) {
                        Text(
                            message,
                            style = AppleType.footnote,
                            color = apple.secondaryLabel,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                        Hairline()
                    }
                    actions.forEachIndexed { i, action ->
                        if (i > 0) Hairline()
                        SheetButton(action.label, if (action.destructive) apple.red else apple.tint, bold = false) {
                            onDismiss()
                            action.onClick()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(if (apple.dark) Color(0xFF2C2C2E) else Color.White)) {
                    SheetButton("Cancel", apple.tint, bold = true, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, color: Color, bold: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(57.dp).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = AppleType.title3.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, fontSize = AppleType.title3.fontSize), color = color)
    }
}

/** A big filled button, like the ones on iOS onboarding screens. */
@Composable
fun FilledButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val apple = LocalApple.current
    Box(
        modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(apple.tint).clickable(onClick = onClick)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppleType.headline, color = Color.White)
    }
}

/** Centred grey message for an empty screen, like "No Photos or Videos". */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = apple.secondaryLabel, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = AppleType.title2, color = apple.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = AppleType.subheadline, color = apple.secondaryLabel, textAlign = TextAlign.Center)
    }
}
