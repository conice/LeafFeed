package me.ash.reader.ui.page.home.flow

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.ash.reader.R
import me.ash.reader.domain.model.constant.ElevationTokens
import me.ash.reader.ui.component.base.ExpressiveIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    value: String,
    placeholder: String = "",
    focusRequester: FocusRequester = remember { FocusRequester() },
    onValueChange: (String) -> Unit = {},
    onClose: () -> Unit = {},
    onSave: (() -> Unit)? = null,
    savedSearchCount: Int = 0,
    onShowSaved: (() -> Unit)? = null,
) {
    val focusManager = LocalFocusManager.current

    var input by remember { mutableStateOf(value) }

    Surface(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp)
            .padding(vertical = 12.dp)
            .height(56.dp)
            .fillMaxWidth(),
        shape = CircleShape,
        tonalElevation = ElevationTokens.Level2.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    modifier = Modifier.padding(start = 16.dp),
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextField(
                    modifier = Modifier
                        .height(56.dp)
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    colors = TextFieldDefaults.colors(
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    value = input,
                    onValueChange = {
                        input = it
                        onValueChange(it)
                    },
                    placeholder = {
                        Text(
                            modifier = Modifier.alpha(0.7f),
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                        }
                    )
                )
            }
            if (input.isNotBlank() && onSave != null) {
                ExpressiveIconButton(onClick = onSave) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = stringResource(R.string.save_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (savedSearchCount > 0 && onShowSaved != null) {
                ExpressiveIconButton(onClick = onShowSaved) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmarks,
                        contentDescription = stringResource(R.string.saved_searches),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            ExpressiveIconButton(onClick = { onClose() }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.clear),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
