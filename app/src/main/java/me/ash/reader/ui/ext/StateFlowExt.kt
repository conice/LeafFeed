package me.ash.reader.ui.ext

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@Composable
fun <T> StateFlow<T>.collectAsStateValue(
    context: CoroutineContext = EmptyCoroutineContext,
): T = collectAsStateWithLifecycle(context = context).value

@Composable
fun <T : R, R> Flow<T>.collectAsStateValue(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext,
): R = collectAsStateWithLifecycle(initialValue = initial, context = context).value

/** Glance compositions do not provide a LifecycleOwner. */
@Composable
fun <T> StateFlow<T>.collectAsStateValueWithoutLifecycle(
    context: CoroutineContext = EmptyCoroutineContext,
): T = collectAsState(context).value

/** Glance compositions do not provide a LifecycleOwner. */
@Composable
fun <T : R, R> Flow<T>.collectAsStateValueWithoutLifecycle(
    initial: R,
    context: CoroutineContext = EmptyCoroutineContext,
): R = collectAsState(initial, context).value
