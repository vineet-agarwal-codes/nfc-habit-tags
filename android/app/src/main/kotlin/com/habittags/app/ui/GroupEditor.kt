package com.habittags.app.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.QuietButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class LauncherApp(val packageName: String, val label: String)

private val IconShape = RoundedCornerShape(10.dp)
private val iconCache = HashMap<String, ImageBitmap?>()

/**
 * Shows the group's installed apps with their icons and lets the user remove apps or pick several to
 * add from a searchable sheet. Listed apps that aren't installed stay in the group, so they're blocked
 * if the user installs them later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditor(groupId: String) {
    val context = LocalContext.current
    val app = context.app()
    val state by app.state.collectAsState()
    val catalog by app.catalog.collectAsState()
    val group = catalog?.appGroups?.firstOrNull { it.id == groupId } ?: return
    val members = state.groupOverrides[groupId] ?: group.androidPackages.toSet()
    val launcherApps = remember { launcherApps(context) }
    val installed = launcherApps.associateBy { it.packageName }
    var picking by remember { mutableStateOf(false) }

    fun save(next: Set<String>) = app.update { it.copy(groupOverrides = it.groupOverrides + (groupId to next)) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(group.title, style = MaterialTheme.typography.titleMedium)
        val shown = members.mapNotNull { installed[it] }.sortedBy { it.label.lowercase() }
        if (shown.isEmpty()) {
            Text("None of these apps are on your phone yet.", style = MaterialTheme.typography.bodyMedium)
        }
        shown.forEach { member ->
            AppRow(member, trailing = { QuietButton("Remove", { save(members - member.packageName) }) })
        }
        val notInstalled = members.size - shown.size
        if (notInstalled > 0) {
            val apps = if (notInstalled == 1) "1 more app" else "$notInstalled more apps"
            Text(
                "Also blocks $apps from this list if you install them later.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        QuietButton("Add apps", { picking = true }, color = MaterialTheme.colorScheme.primary)
    }

    if (picking) {
        var query by remember { mutableStateOf("") }
        var chosen by remember { mutableStateOf(members) }
        ModalBottomSheet(
            onDismissRequest = { picking = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(Modifier.fillMaxHeight(0.92f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Apps to block for ${group.title.lowercase()}", style = MaterialTheme.typography.headlineSmall)
                OutlinedTextField(
                    query, { query = it }, Modifier.fillMaxWidth(),
                    placeholder = { Text("Search your apps") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                )
                val q = query.trim().lowercase()
                // Already chosen apps first, so the user sees what's in the group before adding more.
                val list = launcherApps
                    .filter { q.isEmpty() || q in it.label.lowercase() }
                    .sortedWith(compareBy<LauncherApp> { it.packageName !in members }.thenBy { it.label.lowercase() })
                LazyColumn(Modifier.weight(1f)) {
                    items(list, key = { it.packageName }) { candidate ->
                        val checked = candidate.packageName in chosen
                        val toggle = { chosen = if (checked) chosen - candidate.packageName else chosen + candidate.packageName }
                        Box(Modifier.fillMaxWidth().clickable(onClick = toggle)) {
                            AppRow(candidate, trailing = { Checkbox(checked, { toggle() }) })
                        }
                    }
                }
                val changed = chosen != members
                PrimaryButton(
                    if (changed) "Save" else "Done",
                    {
                        if (changed) save(chosen)
                        picking = false
                    },
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: LauncherApp, trailing: @Composable () -> Unit) {
    val context = LocalContext.current
    val icon by produceState(iconCache[app.packageName], app.packageName) {
        if (!iconCache.containsKey(app.packageName)) {
            value = withContext(Dispatchers.IO) { loadIcon(context, app.packageName) }.also { iconCache[app.packageName] = it }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(IconShape))
        } else {
            Box(Modifier.size(40.dp).clip(IconShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Text(app.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        trailing()
    }
}

private fun launcherApps(context: Context): List<LauncherApp> {
    val pm = context.packageManager
    return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { LauncherApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = runCatching {
    val drawable = context.packageManager.getApplicationIcon(packageName)
    val size = (40 * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, size, size)
    drawable.draw(Canvas(bitmap))
    bitmap.asImageBitmap()
}.getOrNull()
