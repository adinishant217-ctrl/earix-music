package com.maxrave.simpmusic.update

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.maxrave.simpmusic.Platform
import com.maxrave.simpmusic.getPlatform
import org.jetbrains.compose.resources.stringResource
import simpmusic.composeapp.generated.resources.Res
import simpmusic.composeapp.generated.resources.app_up_to_date
import simpmusic.composeapp.generated.resources.cancel
import simpmusic.composeapp.generated.resources.download
import simpmusic.composeapp.generated.resources.later
import simpmusic.composeapp.generated.resources.ok
import simpmusic.composeapp.generated.resources.open_releases_page
import simpmusic.composeapp.generated.resources.up_to_date_message
import simpmusic.composeapp.generated.resources.update_available
import simpmusic.composeapp.generated.resources.update_available_message
import simpmusic.composeapp.generated.resources.update_check_failed
import simpmusic.composeapp.generated.resources.warning

/**
 * Result dialog shared by the Settings row and the cold-start auto-check.
 * Android downloads through [onDownload]; every other platform opens the
 * releases page in a browser through [onOpenReleases].
 */
@Composable
fun UpdateResultDialog(
    result: UpdateCheckResult,
    currentVersion: String,
    onDownload: (UpdateInfo) -> Unit,
    onOpenReleases: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (result) {
        is UpdateCheckResult.Available -> {
            val info = result.info
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.update_available)) },
                text = {
                    Text(
                        stringResource(
                            Res.string.update_available_message,
                            info.version,
                            currentVersion.ifBlank { "?" },
                        ),
                    )
                },
                confirmButton = {
                    if (getPlatform() == Platform.Android) {
                        TextButton(
                            onClick = {
                                onDownload(info)
                                onDismiss()
                            },
                        ) {
                            Text(stringResource(Res.string.download))
                        }
                    } else {
                        TextButton(
                            onClick = {
                                if (info.releaseUrl.isNotBlank()) onOpenReleases(info.releaseUrl)
                                onDismiss()
                            },
                        ) {
                            Text(stringResource(Res.string.open_releases_page))
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.later))
                    }
                },
            )
        }
        is UpdateCheckResult.UpToDate,
        is UpdateCheckResult.NoUpdateFound,
        -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.app_up_to_date)) },
                text = {
                    Text(
                        stringResource(
                            Res.string.up_to_date_message,
                            currentVersion.ifBlank { "?" },
                        ),
                    )
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.ok))
                    }
                },
            )
        }
        is UpdateCheckResult.Error -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text(stringResource(Res.string.warning)) },
                text = { Text(stringResource(Res.string.update_check_failed)) },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(Res.string.cancel))
                    }
                },
            )
        }
    }
}
