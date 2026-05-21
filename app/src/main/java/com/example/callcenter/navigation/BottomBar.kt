package com.example.callcenter.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.rounded.History
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
import androidx.navigation.NavGraph.Companion.findStartDestination
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
    BottomTab(Dest.History.route, "History", Icons.Rounded.History, Icons.Outlined.History),
    BottomTab(Dest.Profile.route, "Profile", Icons.Rounded.Person, Icons.Outlined.Person),
)

@Composable
fun AppBottomBar(navController: NavController) {
    val entry by navController.currentBackStackEntryAsState()
    val current = entry?.destination

    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 8.dp,
    ) {
        bottomTabs.forEach { tab ->
            val selected = current?.hierarchy?.any { it.route == tab.route } == true
            NavigationBarItem(
                selected = selected,
                onClick = {
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
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
