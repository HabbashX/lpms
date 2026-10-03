package com.lpms.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lpms.R
import com.lpms.data.remote.ApiError
import com.lpms.data.remote.ApiResult

/**
 * The four states every list/detail screen can be in.
 *
 * [LpmsErrorState] maps [ApiError] to a message once, centrally, so screens
 * never re-implement "401 means session expired" differently.
 */

@Composable
fun LpmsLoading(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        val text = label ?: stringResource(R.string.common_loading)
        Spacer(Modifier.height(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LpmsEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun LpmsErrorState(
    error: ApiError?,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val message = error.toMessage()
    val offline = error == ApiError.Network

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            if (offline) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (onRetry != null) {
                Spacer(Modifier.height(16.dp))
                LpmsSecondaryButton(
                    text = stringResource(R.string.action_retry),
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
            }
        }
    }
}

/** Compact pill for row-level states (paid / in debt / low stock / expired). */
@Composable
fun LpmsStatusChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Picks a localized, user-facing message for any failure. */
@Composable
fun ApiError?.toMessage(): String = when (this) {
    null, is ApiError.Unknown -> stringResource(R.string.error_generic)
    ApiError.Network -> stringResource(R.string.error_network)
    is ApiError.Serialization -> stringResource(R.string.error_generic)
    is ApiError.Http -> when {
        status == 401 -> stringResource(R.string.error_unauthorized)
        status == 403 -> message ?: stringResource(R.string.error_generic)
        status == 429 -> stringResource(R.string.error_rate_limited)
        else -> message ?: stringResource(R.string.error_generic)
    }
}

/** Converts a result into its UI state in one expression per screen. */
@Composable
fun <T> ApiResult<T>?.asUiState(): LpmsUiState<T> = when (this) {
    null -> LpmsUiState.Idle
    is ApiResult.Success -> LpmsUiState.Ready(value)
    is ApiResult.Failure -> LpmsUiState.Failed(error)
}

sealed interface LpmsUiState<out T> {
    data object Idle : LpmsUiState<Nothing>
    data class Ready<T>(val value: T) : LpmsUiState<T>
    data class Failed(val error: ApiError) : LpmsUiState<Nothing>
}
