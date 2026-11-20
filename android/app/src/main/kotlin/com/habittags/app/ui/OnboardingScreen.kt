package com.habittags.app.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.habittags.app.ui.components.PrimaryButton
import com.habittags.app.ui.components.QuietButton
import com.habittags.app.ui.components.ScreenPadding
import com.habittags.app.ui.theme.SpaceColour
import com.habittags.app.ui.theme.tintColor
import com.habittags.app.ui.theme.toColor

private data class Page(val title: String, val body: String, val colour: SpaceColour, val place: String)

private val PAGES = listOf(
    Page("Shape your spaces", "Your desk, your bed, your front door. Give each place in your home a job, and your phone follows along.", SpaceColour.Tomato, "Desk"),
    Page("Tags make it a tap", "Stick a cheap NFC tag where it happens. Tap your phone on it to switch modes. No tag yet? Start any space from the app.", SpaceColour.DeepBlue, "Bed"),
    Page("Set up your first space", "Pick a recipe, adjust it if you like, and write it to a tag. It takes about a minute.", SpaceColour.Green, "Gym bag"),
)

/** Three short screens on first launch, swipeable. No permissions are asked here. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    Column(Modifier.fillMaxSize().padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Mise", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = ScreenPadding),
        )
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = PAGES[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(Modifier.weight(0.4f))
                // A space card as the picture: the product's own object, not an illustration.
                Column(
                    Modifier.fillMaxWidth().background(p.colour.tintColor(), RoundedCornerShape(28.dp)).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(Modifier.size(14.dp).background(p.colour.field.toColor(), CircleShape))
                    Text(p.place, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(p.title, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PAGES.indices.forEach { i ->
                    Box(
                        Modifier.height(8.dp).width(if (i == page) 24.dp else 8.dp)
                            .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    )
                }
            }
            PrimaryButton(if (page < PAGES.lastIndex) "Next" else "Browse recipes", {
                if (page < PAGES.lastIndex) scope.launch { pager.animateScrollToPage(page + 1) } else onDone()
            }, Modifier.fillMaxWidth())
            if (page < PAGES.lastIndex) QuietButton("Skip", onDone, Modifier.align(Alignment.CenterHorizontally)) else Spacer(Modifier.height(48.dp))
        }
    }
}
