package com.habittags.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private data class LauncherApp(val packageName: String, val label: String)

/**
 * Shows the group's installed apps and lets the user remove or add apps. Listed apps that aren't
 * installed stay in the group, so they're blocked if the user installs them later.
 */
@Composable
fun GroupEditor(groupId: String) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val catalog by app.catalog.collectAsState()
    val group = catalog?.appGroups?.firstOrNull { it.id == groupId } ?: return
    val members = state.groupOverrides[groupId] ?: group.androidPackages.toSet()
    val pm = context.packageManager
    val launcherApps = remember {
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { LauncherApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
    val installed = launcherApps.associateBy { it.packageName }
    var picking by remember { mutableStateOf(false) }

    fun save(next: Set<String>) = app.update { it.copy(groupOverrides = it.groupOverrides + (groupId to next)) }

    Column(Modifier.fillMaxWidth()) {
        Text(group.title, style = MaterialTheme.typography.titleSmall)
        val shown = members.mapNotNull { installed[it] }.sortedBy { it.label.lowercase() }
        if (shown.isEmpty()) Text("None of these apps are on your phone", style = MaterialTheme.typography.bodySmall)
        shown.forEach { member ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(member.label, Modifier.weight(1f))
                Switch(checked = true, onCheckedChange = { save(members - member.packageName) })
            }
        }
        val notInstalled = members.size - shown.size
        if (notInstalled > 0) {
            val apps = if (notInstalled == 1) "1 more app" else "$notInstalled more apps"
            Text("Also blocks $apps from this list if you install them later.", style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = { picking = true }) { Text("Add app") }
        Text("Changes apply the next time a mode starts.", style = MaterialTheme.typography.bodySmall)
    }

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text("Done") } },
            title = { Text("Add to ${group.title}") },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(launcherApps.filter { it.packageName !in members }, key = { it.packageName }) { candidate ->
                        Text(
                            candidate.label,
                            Modifier.fillMaxWidth().clickable { save(members + candidate.packageName); picking = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
        )
    }
}

