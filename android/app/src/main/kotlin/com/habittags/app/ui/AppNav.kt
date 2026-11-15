package com.habittags.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private val TABS = listOf("discover" to "Discover", "my" to "My recipes")

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav(activity: MainActivity) {
    val nav = rememberNavController()
    var checklist by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val intent = activity.pending.value
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val onTab = TABS.any { it.first == route }

    LaunchedEffect(intent) {
        intent ?: return@LaunchedEffect
        intent.getStringExtra(MainActivity.EXTRA_SETUP_TAG_ID)?.let { nav.navigate("setup/$it") }
        intent.getStringExtra(MainActivity.EXTRA_GATE_TAG_ID)?.let { nav.navigate("gate/$it") }
        intent.getStringExtra(MainActivity.EXTRA_CHECKLIST_TITLE)?.let { title ->
            checklist = title to intent.getStringArrayExtra(MainActivity.EXTRA_CHECKLIST_ITEMS).orEmpty().toList()
        }
        activity.pending.value = null
    }

    Scaffold(bottomBar = {
        if (onTab) NavigationBar {
            TABS.forEach { (tab, label) ->
                NavigationBarItem(
                    selected = route == tab,
                    onClick = { nav.openTab(tab) },
                    icon = { Icon(if (tab == "discover") Icons.Filled.Search else Icons.Filled.List, contentDescription = null) },
                    label = { Text(label) },
                )
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (onTab) ActiveModeStrip(onEndNow = { nav.navigate("gate/$it") })
            NavHost(nav, startDestination = "discover") {
                composable("discover") { DiscoverScreen(onOpen = { nav.navigate("recipe/$it") }) }
                composable("my") { MyRecipesScreen(onOpen = { nav.navigate("edit/$it") }, onBrowse = { nav.openTab("discover") }) }
                composable("recipe/{recipeId}") { entry ->
                    val id = entry.arguments!!.getString("recipeId")!!
                    RecipeScreen(id, onInstall = { nav.navigate("install/$id") }, onEdit = { nav.navigate("edit/$id") })
                }
                composable(
                    "install/{recipeId}?setupTagId={setupTagId}",
                    arguments = listOf(navArgument("setupTagId") { type = NavType.StringType; nullable = true }),
                ) { entry ->
                    val id = entry.arguments!!.getString("recipeId")!!
                    InstallScreen(recipeId = id, setupTagId = entry.arguments?.getString("setupTagId"), onDone = {
                        nav.navigate("edit/$id") { popUpTo("discover") }
                    })
                }
                composable("edit/{recipeId}") { entry ->
                    val id = entry.arguments!!.getString("recipeId")!!
                    RecipeEditScreen(
                        id,
                        onOpenTag = { nav.navigate("tag/$it") },
                        onInstallFlow = { nav.navigate("install/$id") },
                        onUninstalled = { nav.popBackStack() },
                    )
                }
                composable("setup/{tagId}") { entry ->
                    val tagId = entry.arguments!!.getString("tagId")!!
                    SetupTagScreen(onPick = { nav.navigate("install/$it?setupTagId=$tagId") })
                }
                composable("tag/{tagId}") { entry ->
                    TagDetailScreen(tagId = entry.arguments!!.getString("tagId")!!, onDone = { nav.popBackStack() })
                }
                composable("gate/{tagId}") { entry ->
                    GateScreen(tagId = entry.arguments!!.getString("tagId")!!, onDone = { nav.popBackStack() })
                }
            }
        }
    }

    checklist?.let { (title, items) -> ChecklistDialog(title, items, onClose = { checklist = null }) }
}
