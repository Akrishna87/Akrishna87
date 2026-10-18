package io.github.akrishna87.rssreader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.data.Feed

/** iOS-style screen: translucent navigation bar, large title that collapses into the bar on scroll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleScreen(
    title: String,
    snackbar: SnackbarHostState,
    modifier: Modifier = Modifier,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    grouped: Boolean = true,
    listState: LazyListState = rememberLazyListState(),
    refreshing: Boolean? = null,
    onRefresh: () -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
    belowTitle: (LazyListScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    val apple = LocalApple.current
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60 } }
    Box(modifier.fillMaxSize().background(if (grouped) apple.groupedBackground else apple.background)) {
        Column(Modifier.fillMaxSize()) {
            NavBar(title = title, showTitle = scrolled, showDivider = scrolled, backLabel = backLabel, onBack = onBack, trailing = trailing)
            val list: @Composable () -> Unit = {
                LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
                    item(key = "__title") {
                        Text(
                            title,
                            style = AppleType.largeTitle,
                            color = apple.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                        )
                    }
                    belowTitle?.invoke(this)
                    content()
                }
            }
            if (refreshing != null) {
                PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) { list() }
            } else {
                list()
            }
        }
        AppleSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** 44dp bar under the status bar: back button with label, centred title, trailing actions. */
@Composable
fun NavBar(
    title: String,
    showTitle: Boolean,
    showDivider: Boolean,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth().background(if (showDivider) apple.bar else Color.Transparent).statusBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(44.dp)) {
            if (onBack != null) {
                Row(
                    Modifier.align(Alignment.CenterStart).fillMaxWidth(0.36f).height(44.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBack)
                        .padding(start = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(AppIcons.ChevronLeft, "Back", tint = apple.tint, modifier = Modifier.size(26.dp))
                    if (backLabel != null) {
                        Text(backLabel, style = AppleType.body, color = apple.tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            AnimatedVisibility(showTitle, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
                Text(
                    title,
                    style = AppleType.headline,
                    color = apple.label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = trailing,
            )
        }
        if (showDivider) Hairline()
    }
}

/** A tinted, borderless icon button sized like a UIBarButtonItem. */
@Composable
fun BarIcon(icon: ImageVector, description: String, enabled: Boolean = true, tint: Color? = null, onClick: () -> Unit) {
    val apple = LocalApple.current
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = ripple(bounded = false, radius = 22.dp), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = (tint ?: apple.tint).copy(alpha = if (enabled) 1f else 0.35f), modifier = Modifier.size(24.dp))
    }
}

/** Tinted text button ("Done", "Add", "Cancel"). */
@Composable
fun BarText(text: String, bold: Boolean = false, enabled: Boolean = true, color: Color? = null, onClick: () -> Unit) {
    val apple = LocalApple.current
    Text(
        text,
        style = AppleType.body.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal),
        color = (color ?: apple.tint).copy(alpha = if (enabled) 1f else 0.35f),
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 0.dp) {
    Box(modifier.padding(start = start).fillMaxWidth().height(0.5.dp).background(LocalApple.current.separator))
}

/** Inset-grouped section (rounded card on the grouped background), with optional header/footer. */
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
            Text(
                header.uppercase(),
                style = AppleType.footnote,
                color = apple.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, bottom = 7.dp),
            )
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(apple.cell), content = content)
        if (footer != null) {
            Text(footer, style = AppleType.footnote, color = apple.secondaryLabel, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Rounded-square symbol tile, like the icons in iOS Settings. */
@Composable
fun SymbolTile(icon: ImageVector, background: Color, size: Dp = 29.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(7.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** A row inside a [GroupedSection]: leading view, title/subtitle, trailing value and chevron. */
@Composable
fun AppleRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    value: String? = null,
    chevron: Boolean = true,
    titleColor: Color? = null,
    showDivider: Boolean = true,
    contentStart: Dp = 16.dp,
    dividerStart: Dp = contentStart + if (leading != null) 44.dp else 0.dp,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = contentStart, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(15.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = AppleType.body, color = titleColor ?: apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = AppleType.footnote, color = apple.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) {
                Spacer(Modifier.width(8.dp))
                Text(value, style = AppleType.body, color = apple.secondaryLabel, maxLines = 1)
            }
            trailing?.invoke(this)
            if (chevron) {
                Spacer(Modifier.width(6.dp))
                Icon(AppIcons.ChevronRight, null, tint = apple.tertiaryLabel, modifier = Modifier.size(18.dp))
            }
        }
        if (showDivider) Hairline(start = dividerStart)
    }
}

/** iOS segmented control. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val apple = LocalApple.current
    Row(
        modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(9.dp)).background(apple.fill).padding(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val isSel = i == selected
            Box(
                Modifier.weight(1f).fillMaxSize()
                    .then(if (isSel) Modifier.shadow(1.5.dp, RoundedCornerShape(7.dp)).clip(RoundedCornerShape(7.dp)).background(apple.selectedSegment) else Modifier)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = AppleType.footnote.copy(fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium), color = apple.label)
            }
        }
    }
}

/** Borderless text field with a placeholder, for use inside grouped rows. */
@Composable
fun AppleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: TextStyle = AppleType.body,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val apple = LocalApple.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        singleLine = true,
        textStyle = style.copy(color = apple.label),
        cursorBrush = SolidColor(apple.tint),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        modifier = modifier,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, style = style, color = apple.tertiaryLabel, maxLines = 1)
                inner()
            }
        },
    )
}

/** Rounded search field ("Search") like UISearchBar. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String = "Search", modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    Row(
        modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(apple.fill).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Magnifier, null, tint = apple.secondaryLabel, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        AppleTextField(value, onValueChange, placeholder, Modifier.weight(1f), style = AppleType.body)
        if (value.isNotEmpty()) {
            Icon(AppIcons.Close, "Clear", tint = apple.secondaryLabel, modifier = Modifier.size(16.dp).clickable { onValueChange("") })
        }
    }
}

/** Green iOS switch. */
@Composable
fun AppleSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val apple = LocalApple.current
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedTrackColor = apple.green,
            checkedThumbColor = Color.White,
            checkedBorderColor = Color.Transparent,
            uncheckedTrackColor = apple.fill,
            uncheckedThumbColor = Color.White,
            uncheckedBorderColor = Color.Transparent,
        ),
        thumbContent = null,
    )
}

/** Capsule button ("GET"-style) used for Add in Discover. */
@Composable
fun CapsuleButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val apple = LocalApple.current
    Text(
        text,
        style = AppleType.subheadline.copy(fontWeight = FontWeight.Bold),
        color = apple.tint.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(apple.fill).clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
    )
}

/** Floating pill for transient messages (instead of a Material snackbar). */
@Composable
fun AppleSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    SnackbarHost(state, modifier.navigationBarsPadding().padding(bottom = 16.dp)) { data ->
        Text(
            data.visuals.message,
            style = AppleType.subheadline.copy(fontWeight = FontWeight.Medium),
            color = if (apple.dark) Color.Black else Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp).shadow(8.dp, RoundedCornerShape(50))
                .clip(RoundedCornerShape(50)).background(if (apple.dark) Color(0xFFEDEDED) else Color(0xE6202022))
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/** Letter avatar for a feed, in a soft rounded square. */
@Composable
fun FeedBadge(feed: Feed, size: Dp = 29.dp) {
    val hues = listOf(0xFFFF9500, 0xFF34C759, 0xFF007AFF, 0xFFAF52DE, 0xFF5AC8FA, 0xFFFF2D55, 0xFFA2845E, 0xFF5856D6, 0xFFFF3B30, 0xFF30B0C7)
    val color = Color(hues[(feed.id.hashCode() and 0x7fffffff) % hues.size])
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.24f)).background(color), contentAlignment = Alignment.Center) {
        Text(
            feed.name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            style = AppleType.subheadline.copy(fontWeight = FontWeight.Bold),
        )
    }
}

/** Centered "empty" message for lists. */
@Composable
fun EmptyMessage(title: String, message: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit = {}) {
    val apple = LocalApple.current
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = AppleType.title2, color = apple.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = AppleType.subheadline, color = apple.secondaryLabel, textAlign = TextAlign.Center)
        content()
    }
}

/** Bottom toolbar (UIToolbar) with evenly spaced items. */
@Composable
fun BottomToolbar(content: @Composable RowScope.() -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth().background(apple.bar)) {
        Hairline()
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(48.dp).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** iOS-style alert: centred title and message, tinted actions, optional destructive (red) confirm. */
@Composable
fun AppleAlert(
    title: String,
    message: String,
    confirm: String,
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val apple = LocalApple.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (apple.dark) Color(0xFF2C2C2E) else Color(0xFFF2F2F2),
        shape = RoundedCornerShape(14.dp),
        title = { Text(title, style = AppleType.headline, color = apple.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(message, style = AppleType.footnote, color = apple.label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            BarText(confirm, bold = true, color = if (destructive) apple.red else apple.tint, onClick = onConfirm)
        },
        dismissButton = { BarText("Cancel", onClick = onDismiss) },
    )
}
