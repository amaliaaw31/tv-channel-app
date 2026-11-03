package com.projectyaya.tvchannel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.delay

/**
 * The search box is a plain D-pad focusable row until OK is pressed: only then does the text field exist, take focus
 * and open the on-screen keyboard. A text field that is merely focused opens the keyboard by itself and the keyboard
 * swallows the D-pad, which trapped focus in the box.
 *
 * Ending the edit: the keyboard's Search key and a Back that reaches the app call [onDone] (focus goes to the list).
 * The TV keyboard also eats the first Back itself, closing without telling the app (measured: its window reports no
 * insets and no visible height), so the box then still has focus with no keyboard; any D-pad key reaches it only in that
 * case, and moving focus away simply ends the edit.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun SearchField(query: String, onChange: (String) -> Unit, enabled: Boolean, onDone: () -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    var editing by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // The effects below outlive many recompositions (typing refilters the list): they must hand focus to the
    // list as it is now, not as it was when editing began.
    val latestOnDone by rememberUpdatedState(onDone)

    fun finish() {
        keyboard?.hide()
        editing = false
        hadFocus = false
        latestOnDone()
    }

    if (!editing) {
        Surface(
            onClick = { editing = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.08f),
                contentColor = Color.White,
                focusedContainerColor = accent.copy(alpha = 0.25f),
                focusedContentColor = Color.White,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), shape = shape),
                focusedBorder = Border(BorderStroke(3.dp, accent), shape = shape),
            ),
        ) {
            Text(
                query.ifEmpty { "Cari channel…" },
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        return
    }

    BackHandler { finish() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { fieldFocus.requestFocus() }
        delay(150) // the text input session starts a moment after focus; show() before that is ignored
        keyboard?.show()
    }
    BasicTextField(
        value = query,
        onValueChange = onChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { finish() }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(fieldFocus)
            // These keys reach the field only while no keyboard is up (the keyboard takes them first). The field
            // would swallow them, so move focus by hand; OK brings the keyboard back.
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = when (e.key) {
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        keyboard?.show()
                        return@onPreviewKeyEvent true
                    }
                    else -> return@onPreviewKeyEvent false
                }
                focusManager.moveFocus(direction)
                true
            }
            .onFocusChanged {
                if (it.isFocused) hadFocus = true
                else if (hadFocus) { // focus moved on (D-pad out of the box): the edit is over
                    keyboard?.hide()
                    editing = false
                    hadFocus = false
                }
            }
            .background(accent.copy(alpha = 0.25f), shape)
            .border(3.dp, accent, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Selection follows focus, like Android TV tab rows: moving onto a tab filters immediately. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun CategoryTabs(categories: List<String>, selected: String?, onSelect: (String?) -> Unit, enabled: Boolean) {
    val tabs = listOf<String?>(null) + categories
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(50)
    LazyRow(contentPadding = PaddingValues(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tabs, key = { it ?: "all" }) { id ->
            Surface(
                onClick = { onSelect(id) },
                enabled = enabled,
                modifier = Modifier.onFocusChanged { if (it.hasFocus) onSelect(id) },
                shape = ClickableSurfaceDefaults.shape(shape),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (id == selected) accent.copy(alpha = 0.35f) else Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    focusedContainerColor = accent.copy(alpha = 0.55f),
                    focusedContentColor = Color.White,
                ),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, accent), shape = shape)),
            ) {
                Text(
                    if (id == null) "Semua" else categoryLabel(id),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}
