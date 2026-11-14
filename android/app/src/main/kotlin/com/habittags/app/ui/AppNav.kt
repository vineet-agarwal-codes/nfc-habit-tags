package com.habittags.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

@Composable
fun AppNav(activity: MainActivity) {
    val nav = rememberNavController()
    var checklist by remember { mutableStateOf<Pair<String, List<String>>?>(null) }
    val intent = activity.pending.value

    LaunchedEffect(intent) {
        intent ?: return@LaunchedEffect
        intent.getStringExtra(MainActivity.EXTRA_SETUP_TAG_ID)?.let { nav.navigate("catalog?setupTagId=$it") }
        intent.getStringExtra(MainActivity.EXTRA_GATE_TAG_ID)?.let { nav.navigate("gate/$it") }
        intent.getStringExtra(MainActivity.EXTRA_CHECKLIST_TITLE)?.let { title ->
            checklist = title to intent.getStringArrayExtra(MainActivity.EXTRA_CHECKLIST_ITEMS).orEmpty().toList()
        }
        activity.pending.value = null
    }

    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onAddTag = { nav.navigate("catalog") },
                onOpenTag = { nav.navigate("tag/$it") },
                onEndNow = { nav.navigate("gate/$it") },
            )
        }
        composable(
            "catalog?setupTagId={setupTagId}",
            arguments = listOf(navArgument("setupTagId") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            val setupTagId = entry.arguments?.getString("setupTagId")
            CatalogScreen(setupTagId = setupTagId, onPick = { recipeId ->
                nav.navigate("install/$recipeId" + (setupTagId?.let { "?setupTagId=$it" } ?: ""))
            })
        }
        composable(
            "install/{recipeId}?setupTagId={setupTagId}",
            arguments = listOf(navArgument("setupTagId") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            InstallScreen(
                recipeId = entry.arguments!!.getString("recipeId")!!,
                setupTagId = entry.arguments?.getString("setupTagId"),
                onDone = { nav.popBackStack("home", inclusive = false) },
            )
        }
        composable("tag/{tagId}") { entry ->
            TagDetailScreen(tagId = entry.arguments!!.getString("tagId")!!, onDone = { nav.popBackStack() })
        }
        composable("gate/{tagId}") { entry ->
            GateScreen(tagId = entry.arguments!!.getString("tagId")!!, onDone = { nav.popBackStack("home", inclusive = false) })
        }
    }

    checklist?.let { (title, items) -> ChecklistDialog(title, items, onClose = { checklist = null }) }
}
