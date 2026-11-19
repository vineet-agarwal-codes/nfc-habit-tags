package com.habittags.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private val TABS = listOf("home" to "Home", "discover" to "Discover")

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav(activity: MainActivity) {
    val app = LocalContext.current.app()
    val state by app.state.collectAsState()
    if (!state.onboarded) {
        OnboardingScreen(onDone = { app.update { it.copy(onboarded = true) }; activity.pending.value = activity.pending.value ?: android.content.Intent().putExtra(OPEN_DISCOVER, true) })
        return
    }
    val nav = rememberNavController()
    var checklist by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val intent = activity.pending.value
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val onTab = TABS.any { it.first == route }

    LaunchedEffect(intent) {
        intent ?: return@LaunchedEffect
        if (intent.getBooleanExtra(OPEN_DISCOVER, false)) nav.openTab("discover")
        intent.getStringExtra(MainActivity.EXTRA_SETUP_TAG_ID)?.let { nav.navigate("setup/$it") }
        intent.getStringExtra(MainActivity.EXTRA_GATE_TAG_ID)?.let { nav.navigate("gate/$it") }
        intent.getStringExtra(MainActivity.EXTRA_CHECKLIST_TITLE)?.let { title ->
            checklist = title to intent.getStringArrayExtra(MainActivity.EXTRA_CHECKLIST_ITEMS).orEmpty().toList()
        }
        activity.pending.value = null
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (onTab) NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                TABS.forEach { (tab, label) ->
                    NavigationBarItem(
                        selected = route == tab,
                        onClick = { nav.openTab(tab) },
                        icon = { Icon(if (tab == "home") Icons.Filled.Home else Icons.Filled.Search, contentDescription = null) },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            NavHost(nav, startDestination = "home") {
                composable("home") {
                    HomeScreen(
                        onOpenSpace = { nav.navigate("space/$it") },
                        onOpenRunning = { nav.navigate("running/$it") },
                        onEndEarly = { nav.navigate("gate/$it") },
                        onBrowse = { nav.openTab("discover") },
                    )
                }
                composable("discover") {
                    DiscoverScreen(onOpen = { nav.navigate("recipe/$it") }, onAdd = { nav.navigate("add/$it") })
                }
                composable("recipe/{recipeId}") { entry ->
                    val id = entry.arguments!!.getString("recipeId")!!
                    RecipeScreen(id, onAdd = { nav.navigate("add/$id") }, onOpenSpace = { nav.navigate("space/$id") })
                }
                composable(
                    "add/{recipeId}?setupTagId={setupTagId}",
                    arguments = listOf(navArgument("setupTagId") { type = NavType.StringType; nullable = true }),
                ) { entry ->
                    val id = entry.arguments!!.getString("recipeId")!!
                    AddFlow(recipeId = id, setupTagId = entry.arguments?.getString("setupTagId"), onDone = {
                        nav.navigate("home") { popUpTo("home") { inclusive = true } }
                    })
                }
                composable("space/{recipeId}") { entry ->
                    val id = entry.arguments!!.getString("recipeId")!!
                    SpaceSettingsScreen(
                        id,
                        onOpenTag = { nav.navigate("tag/$it") },
                        onAddFlow = { nav.navigate("add/$id") },
                        onRemoved = { nav.popBackStack() },
                    )
                }
                composable("running/{modeKey}") { entry ->
                    val key = entry.arguments!!.getString("modeKey")!!
                    RunningScreen(key, onEndEarly = { nav.navigate("gate/$key") }, onClose = { nav.popBackStack() })
                }
                composable("setup/{tagId}") { entry ->
                    val tagId = entry.arguments!!.getString("tagId")!!
                    SetupTagScreen(onPick = { nav.navigate("add/$it?setupTagId=$tagId") })
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

private const val OPEN_DISCOVER = "openDiscover"
