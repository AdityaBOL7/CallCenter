package com.example.callcenter.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.callcenter.domain.model.AuthStatus
import com.example.callcenter.ui.screens.auth.LoginScreen
import com.example.callcenter.ui.screens.callbacks.CallbackListScreen
import com.example.callcenter.ui.screens.callbacks.ScheduleCallbackScreen
import com.example.callcenter.ui.screens.calls.CallHistoryScreen
import com.example.callcenter.ui.screens.calls.CallScreen
import com.example.callcenter.ui.screens.calls.DispositionScreen
import com.example.callcenter.ui.screens.campaigns.CampaignDetailScreen
import com.example.callcenter.ui.screens.campaigns.CampaignListScreen
import com.example.callcenter.ui.screens.dashboard.DashboardScreen
import com.example.callcenter.ui.screens.leads.LeadDetailScreen
import com.example.callcenter.ui.screens.leads.LeadListScreen
import com.example.callcenter.ui.screens.notifications.NotificationsScreen
import com.example.callcenter.ui.screens.profile.EditProfileScreen
import com.example.callcenter.ui.screens.profile.HelpSupportScreen
import com.example.callcenter.ui.screens.profile.ProfileScreen
import com.example.callcenter.ui.screens.profile.SettingsScreen
import com.example.callcenter.ui.screens.profile.TermsPrivacyScreen
import com.example.callcenter.ui.screens.reports.ReportsScreen

@Composable
fun AppNavGraph(rootViewModel: RootViewModel = hiltViewModel()) {
    val authStatus by rootViewModel.authStatus.collectAsState()
    val rootNav = rememberNavController()

    val startDestination = when (authStatus) {
        AuthStatus.AUTHENTICATED -> Dest.MainTabs.route
        else -> Dest.Login.route
    }

    NavHost(
        navController = rootNav,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(280)) { it / 2 } + fadeIn(tween(220)) },
        exitTransition = { slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(180)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(180)) },
    ) {
        // Auth
        composable(Dest.Login.route) {
            LoginScreen(
                onAuthenticated = {
                    rootNav.navigate(Dest.MainTabs.route) {
                        popUpTo(Dest.Login.route) { inclusive = true }
                    }
                },
            )
        }

        // Main bottom-bar host
        composable(Dest.MainTabs.route) {
            MainScaffold(rootNav)
        }

        // Modal/stack
        composable(Dest.LeadDetail.route) { entry ->
            val id = entry.arguments?.getString(Dest.LeadDetail.ARG)?.toIntOrNull() ?: 0
            LeadDetailScreen(
                leadId = id,
                onBack = { rootNav.popBackStack() },
                onCall = { callId, route ->
                    rootNav.navigate(Dest.Call.build(callId, id, route))
                },
                onScheduleCallback = { rootNav.navigate(Dest.ScheduleCallback.build(id)) },
            )
        }
        composable(
            Dest.Call.route,
            enterTransition = { fadeIn(tween(300)) },
            exitTransition = { fadeOut(tween(200)) },
        ) { entry ->
            val callId = entry.arguments?.getString(Dest.Call.ARG_CALL).orEmpty()
            val leadId = entry.arguments?.getString(Dest.Call.ARG_LEAD)?.toIntOrNull() ?: 0
            val route = entry.arguments?.getString(Dest.Call.ARG_ROUTE).orEmpty()
            CallScreen(
                callId = callId,
                leadId = leadId,
                routeType = route,
                onEnded = {
                    rootNav.navigate(Dest.Disposition.build(callId, leadId)) {
                        popUpTo(Dest.Call.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Dest.Disposition.route) { entry ->
            val callId = entry.arguments?.getString(Dest.Disposition.ARG_CALL).orEmpty()
            val leadId = entry.arguments?.getString(Dest.Disposition.ARG_LEAD)?.toIntOrNull() ?: 0
            DispositionScreen(
                callId = callId,
                leadId = leadId,
                onDone = { rootNav.popBackStack(Dest.MainTabs.route, false) },
                onNextCall = { nextLeadId, nextCallId, route ->
                    // Auto-dial: jump straight to the next call, dropping this
                    // disposition off the back stack so the loop doesn't pile up.
                    rootNav.navigate(Dest.Call.build(nextCallId, nextLeadId, route)) {
                        popUpTo(Dest.Disposition.route) { inclusive = true }
                    }
                },
            )
        }
        composable(Dest.ScheduleCallback.route) { entry ->
            val leadId = entry.arguments?.getString(Dest.ScheduleCallback.ARG)?.toIntOrNull() ?: 0
            ScheduleCallbackScreen(leadId = leadId, onDone = { rootNav.popBackStack() })
        }
        composable(Dest.CampaignList.route) {
            CampaignListScreen(
                onBack = { rootNav.popBackStack() },
                onCampaign = { rootNav.navigate(Dest.CampaignDetail.build(it)) },
            )
        }
        composable(Dest.CampaignDetail.route) { entry ->
            val id = entry.arguments?.getString(Dest.CampaignDetail.ARG)?.toIntOrNull() ?: 0
            CampaignDetailScreen(campaignId = id, onBack = { rootNav.popBackStack() })
        }
        composable(Dest.Notifications.route) {
            NotificationsScreen(onBack = { rootNav.popBackStack() })
        }
        composable(Dest.Settings.route) {
            SettingsScreen(onBack = { rootNav.popBackStack() })
        }
        composable(Dest.EditProfile.route) {
            EditProfileScreen(onBack = { rootNav.popBackStack() })
        }
        composable(Dest.HelpSupport.route) {
            HelpSupportScreen(onBack = { rootNav.popBackStack() })
        }
        composable(Dest.TermsPrivacy.route) {
            TermsPrivacyScreen(onBack = { rootNav.popBackStack() })
        }
        composable(Dest.Reports.route) {
            ReportsScreen(onBack = { rootNav.popBackStack() })
        }
    }
}

@Composable
private fun MainScaffold(rootNav: NavHostController) {
    val tabsNav = rememberNavController()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { AppBottomBar(tabsNav) },
    ) { padding ->
        TabsNavHost(
            tabsNav = tabsNav,
            rootNav = rootNav,
            padding = padding,
        )
    }
}

@Composable
private fun TabsNavHost(
    tabsNav: NavHostController,
    rootNav: NavHostController,
    padding: PaddingValues,
) {
    NavHost(
        navController = tabsNav,
        startDestination = Dest.Dashboard.route,
        modifier = Modifier.padding(padding),
    ) {
        composable(Dest.Dashboard.route) {
            DashboardScreen(
                onOpenLeads = { tabsNav.navigate(Dest.Leads.route) },
                onOpenCallbacks = { tabsNav.navigate(Dest.Callbacks.route) },
                onOpenHistory = { tabsNav.navigate(Dest.History.route) },
                onOpenNotifications = { rootNav.navigate(Dest.Notifications.route) },
                onOpenCampaigns = { rootNav.navigate(Dest.CampaignList.route) },
                onOpenReports = { rootNav.navigate(Dest.Reports.route) },
                onOpenSettings = { rootNav.navigate(Dest.Settings.route) },
            )
        }
        composable(Dest.Leads.route) {
            LeadListScreen(
                onLeadClick = { rootNav.navigate(Dest.LeadDetail.build(it)) },
                onStartCall = { leadId, callId, route ->
                    rootNav.navigate(Dest.Call.build(callId, leadId, route))
                },
            )
        }
        composable(Dest.Callbacks.route) {
            CallbackListScreen(
                onSchedule = { leadId -> rootNav.navigate(Dest.ScheduleCallback.build(leadId)) },
                onCall = { leadId, callId, route ->
                    rootNav.navigate(Dest.Call.build(callId, leadId, route))
                },
            )
        }
        composable(Dest.History.route) {
            CallHistoryScreen()
        }
        composable(Dest.Profile.route) {
            ProfileScreen(
                onEditProfile = { rootNav.navigate(Dest.EditProfile.route) },
                onSettings = { rootNav.navigate(Dest.Settings.route) },
                onReports = { rootNav.navigate(Dest.Reports.route) },
                onHelp = { rootNav.navigate(Dest.HelpSupport.route) },
                onTerms = { rootNav.navigate(Dest.TermsPrivacy.route) },
                onSignOut = { rootViewModelSignOut(rootNav) },
            )
        }
    }
}

private fun rootViewModelSignOut(rootNav: NavHostController) {
    // Sign-out navigates back to login; AuthRepository will be invalidated by ProfileScreen's VM.
    rootNav.navigate(Dest.Login.route) {
        popUpTo(0) { inclusive = true }
    }
}
