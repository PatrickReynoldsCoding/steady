package com.patrickreynolds.steady.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.patrickreynolds.steady.BuildConfig
import com.patrickreynolds.steady.Updater
import com.patrickreynolds.steady.Updater.State

/** Back from Android's "install unknown apps" page: carry on with the install. */
@Composable
private fun ResumeInstall() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) Updater.continueInstall(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

/** Home screen: only appears when there is an update to act on. One tap to update. */
@Composable
fun UpdatePanel() {
    val context = LocalContext.current
    ResumeInstall()
    val s = Updater.state
    val release = when (s) {
        is State.Available -> s.release
        is State.Downloading -> s.release
        is State.NeedsPermission -> s.release
        is State.Installing -> s.release
        is State.Failed -> s.release
        else -> null
    } ?: return
    Panel(color = MaterialTheme.colorScheme.primaryContainer) {
        Text("Update to ${release.version}", style = MaterialTheme.typography.titleLarge)
        when (s) {
            is State.Available -> {
                Text("A new version of Steady is ready. Your data is kept.", style = MaterialTheme.typography.bodyLarge)
                BigButton("Update", { Updater.downloadAndInstall(context, release) })
            }
            is State.Downloading -> {
                Text("Downloading… ${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(8.dp))
            }
            is State.NeedsPermission -> {
                Text("Android needs your OK once. Turn on \"Allow from this source\", then come back.", style = MaterialTheme.typography.bodyLarge)
                BigButton("Open the setting", { context.startActivity(Updater.permissionSettings(context)) })
            }
            is State.Installing -> Text("Installing… Steady closes for a moment. Open it again afterwards.", style = MaterialTheme.typography.bodyLarge)
            is State.Failed -> {
                Text(s.message, style = MaterialTheme.typography.bodyLarge, color = LocalStateColors.current.stop)
                BigButton("Try again", { Updater.downloadAndInstall(context, release) })
            }
            else -> Unit
        }
    }
}

/** Settings: the version, a manual check, and whether to check on opening. */
@Composable
fun UpdatesSection() {
    val context = LocalContext.current
    ResumeInstall()
    val s = Updater.state
    Panel {
        Text("Updates", style = MaterialTheme.typography.titleLarge)
        Text("This is Steady ${BuildConfig.VERSION_NAME}.", style = MaterialTheme.typography.bodyLarge)
        when (s) {
            is State.Checking -> Text("Checking…", style = MaterialTheme.typography.bodyMedium)
            is State.UpToDate -> Text("Up to date.", style = MaterialTheme.typography.bodyMedium)
            is State.Available -> Text("Version ${s.release.version} is ready. Update from the home screen.", style = MaterialTheme.typography.bodyMedium)
            is State.Failed -> if (s.release == null) Text(s.message, style = MaterialTheme.typography.bodyMedium, color = LocalStateColors.current.stop)
            else -> Unit
        }
        TextButton(onClick = { Updater.check(context) }) { Text("Check now") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Check when Steady opens", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = Updater.autoCheck, onCheckedChange = { Updater.changeAutoCheck(it) })
        }
        Text("At most once a day. Steady only goes online for this, to GitHub.", style = MaterialTheme.typography.bodySmall, color = LocalStateColors.current.muted)
    }
}
