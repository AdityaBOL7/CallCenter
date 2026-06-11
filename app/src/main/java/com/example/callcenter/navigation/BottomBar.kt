package com.example.callcenter.navigation

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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.callcenter.ui.theme.Brand500
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

@Composable
fun AppBottomBar(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination
    // The tabs NavHost starts on Dashboard; that's our back-stack root ("Home").
    // Use the constant directly — reading navController.graph here would crash on
    // first composition (graph isn't set until the NavHost composes).
    val startRoute = Dest.Dashboard.route

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
    ) {
        bottomTabs.forEach { tab ->
            val selected = current?.hierarchy?.any { it.route == tab.route } == true
            val isHome = tab.route == startRoute
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (selected) return@NavigationBarItem
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
                icon = {
                    Icon(
                        imageVector = if (selected) tab.iconSelected else tab.iconUnselected,
                        contentDescription = tab.label,
                    )
                },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Brand500,
                    selectedTextColor = Brand500,
                    indicatorColor = Color.Transparent,
                    unselectedIconColor = Ink400,
                    unselectedTextColor = Ink400,
                ),
            )
        }
    }
}
