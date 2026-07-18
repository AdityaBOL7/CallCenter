package com.example.callcenter.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.callcenter.ui.theme.Brand500
import com.example.callcenter.ui.theme.Brand600
import com.example.callcenter.ui.theme.Ink400

data class BottomTab(
    val route: String,
    val label: String,
    val iconSelected: ImageVector,
    val iconUnselected: ImageVector,
)

val bottomTabs = listOf(
    BottomTab(Dest.Dashboard.route, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    BottomTab(Dest.Leads.route, "Leads", Icons.Rounded.People, Icons.Outlined.People),
    BottomTab(Dest.Callbacks.route, "Follow-ups", Icons.Rounded.Schedule, Icons.Outlined.Schedule),
    BottomTab(Dest.Profile.route, "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
)

/**
 * Floating, iOS-style bottom navigation: a rounded "island" that hovers above the
 * bottom edge with a soft shadow. The selected tab expands into a tinted capsule
 * that reveals its label; unselected tabs collapse to an icon only. Everything
 * animates with a spring so taps feel springy rather than instant.
 *
 * Hosted in the Scaffold's bottomBar slot — its container is transparent, so the
 * pill (not a full-width bar) is what the user sees, and content still reserves
 * space for the pill's footprint via the slot's measured height.
 */
@Composable
fun AppBottomBar(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    // The tabs NavHost starts on Dashboard; that's our back-stack root ("Home").
    // Use the constant directly — reading navController.graph here would crash on
    // first composition (graph isn't set until the NavHost composes).
    val startRoute = Dest.Dashboard.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Float above the gesture/nav-bar inset, with breathing room on the sides.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = 16.dp,
                    shape = RoundedCornerShape(28.dp),
                    ambientColor = Brand600.copy(alpha = 0.18f),
                    spotColor = Brand600.copy(alpha = 0.22f),
                )
                .background(Color.White, RoundedCornerShape(28.dp))
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            bottomTabs.forEach { tab ->
                val selected = current?.hierarchy?.any { it.route == tab.route } == true
                val isHome = tab.route == startRoute
                FloatingNavItem(
                    tab = tab,
                    selected = selected,
                    onClick = {
                        if (selected) return@FloatingNavItem
                        navController.navigate(tab.route) {
                            // Keep Home (the start destination) as the permanent back-stack
                            // root so Back from any tab returns to Home, and Back from Home
                            // exits the app — the standard bottom-nav behavior. For Home we
                            // pop *inclusively* and don't save its state, so it's the single
                            // root rather than stacking a second Home entry.
                            popUpTo(startRoute) {
                                saveState = !isHome
                                inclusive = isHome
                            }
                            launchSingleTop = true
                            restoreState = !isHome
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FloatingNavItem(
    tab: BottomTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The selected capsule's tint, icon/label color, and width all animate together
    // so the active tab "grows" into a pill while the others stay compact.
    val capsuleColor by animateColorAsState(
        targetValue = if (selected) Brand500.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "capsule",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) Brand600 else Ink400,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "content",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "iconScale",
    )
    val horizontalPadding by animateDpAsState(
        targetValue = if (selected) 14.dp else 12.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pad",
    )

    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(capsuleColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (selected) tab.iconSelected else tab.iconUnselected,
                contentDescription = tab.label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale),
            )
            // The label only exists for the selected tab; AnimatedVisibility expands
            // it from the left so the capsule appears to grow outward.
            androidx.compose.animation.AnimatedVisibility(visible = selected) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                    Text(
                        text = tab.label,
                        color = contentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
