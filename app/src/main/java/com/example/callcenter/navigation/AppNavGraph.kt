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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.callcenter.domain.model.AuthStatus
import com.example.callcenter.telephony.RequestCallPermissions
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
import com.example.callcenter.ui.screens.splash.SplashScreen

@Composable
fun AppNavGraph(rootViewModel: RootViewModel = hiltViewModel()) {
    val authStatus by rootViewModel.authStatus.collectAsStateWithLifecycle()
    val rootNav = rememberNavController()

    // Cold start always begins on the animated brand splash; it routes onward to
    // Login or MainTabs (based on authStatus) once its animation finishes.
    val startDestination = Dest.Splash.route

    // An answered incoming SIM call needs its outcome logged — route straight to
    // the incoming disposition screen. While the splash/login own the screen this
    // is skipped: the persisted pending incoming (written before the emit) routes
    // there after the splash, and emissions can't happen while logged out.
    LaunchedEffect(Unit) {
        rootViewModel.incomingDispositions.collect {
            val current = rootNav.currentDestination?.route
            if (current != null && current != Dest.Splash.route && current != Dest.Login.route) {
                rootNav.navigate(Dest.DispositionIncoming.route) { launchSingleTop = true }
            }
        }
    }

    // When the session ends (sign-out OR a dead refresh token via
    // AuthRepository.sessionExpired), return to Login and clear the back stack.
    // Skipped while the splash is still showing — the splash owns the first
    // route off itself, so it must not race with this effect.
    //
    // Why combine() instead of LaunchedEffect(authStatus): the status flip can
    // land at a moment this can't act on (mid-transition, destination null, or
    // while the effect is being recomposed). A keyed effect fires ONCE for that
    // value and never again — leaving a "zombie" UI: agent parked on the
    // Dashboard with an empty token store, every request 401ing silently (seen
    // in the field 2026-07-16). Re-evaluating on EVERY navigation change as
    // well as every status change means the bounce to Login can't be missed.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.flow.combine(
            rootViewModel.authStatus,
            rootNav.currentBackStackEntryFlow,
        ) { status, entry -> status to entry.destination.route }
            .collect { (status, route) ->
                if (status == AuthStatus.UNAUTHENTICATED &&
                    route != null && route != Dest.Login.route && route != Dest.Splash.route
                ) {
                    rootNav.navigate(Dest.Login.route) {
                        popUpTo(rootNav.graph.id) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
    }

    NavHost(
        navController = rootNav,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally(tween(280)) { it / 2 } + fadeIn(tween(220)) },
        exitTransition = { slideOutHorizontally(tween(220)) { -it / 6 } + fadeOut(tween(180)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 6 } + fadeIn(tween(220)) },
        popExitTransition = { slideOutHorizontally(tween(220)) { it / 2 } + fadeOut(tween(180)) },
    ) {
        // Animated brand splash → routes onward by auth state, clearing itself.
        composable(Dest.Splash.route) {
            val scope = rememberCoroutineScope()
            SplashScreen(
                onFinished = {
                    scope.launch {
                        if (authStatus == AuthStatus.AUTHENTICATED) {
                            // If a disposition was left un-submitted (app killed on that
                            // screen), force the agent straight back to it. Otherwise Home.
                            val pending = rootViewModel.pendingDisposition()
                            val pendingIncoming = pending == null && rootViewModel.hasPendingIncoming()
                            rootNav.navigate(Dest.MainTabs.route) {
                                popUpTo(Dest.Splash.route) { inclusive = true }
                                launchSingleTop = true
                            }
                            if (pending != null) {
                                rootNav.navigate(Dest.Disposition.build(pending.first, pending.second)) {
                                    launchSingleTop = true
                                }
                            } else if (pendingIncoming) {
                                rootNav.navigate(Dest.DispositionIncoming.route) {
                                    launchSingleTop = true
                                }
                            }
                        } else {
                            rootNav.navigate(Dest.Login.route) {
                                popUpTo(Dest.Splash.route) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                },
            )
        }

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
            // Ask for calling permissions here — the first authenticated screen
            // after login. Denied ones are re-requested at the point of use.
            RequestCallPermissions()
            // Auto-refresh on every RETURN to the foreground (app switch, screen
            // back on): leads/callbacks/profile silently re-fetch, so stale data
            // and a no-network launch heal without a manual pull. The FIRST
            // ON_START is skipped — it's replayed at composition, and each
            // screen already fetches on its ViewModel init.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                var first = true
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_START) {
                        if (first) first = false else rootViewModel.refreshOnForeground()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            MainScaffold(rootNav)
        }

        // Modal/stack
        composable(Dest.LeadDetail.route) { entry ->
            val id = entry.arguments?.getString(Dest.LeadDetail.ARG)?.toIntOrNull() ?: 0
            LeadDetailScreen(
                leadId = id,
                onBack = { rootNav.popBackStack() },
                onCall = { callId, route ->
                    rootNav.navigate(Dest.Call.build(callId, id, route)) { launchSingleTop = true }
                },
                onScheduleCallback = {
                    rootNav.navigate(Dest.ScheduleCallback.build(id)) { launchSingleTop = true }
                },
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
        // Answered incoming SIM call: same screen, but cancellable (a personal
        // call has no business outcome) and it owns the deferred sim-incoming POST.
        composable(Dest.DispositionIncoming.route) {
            DispositionScreen(
                callId = "",
                leadId = 0,
                incoming = true,
                onDone = { rootNav.popBackStack(Dest.MainTabs.route, false) },
                // "Redial" here calls the inbound caller back — route to the call
                // screen exactly as the outbound disposition does.
                onNextCall = { nextLeadId, nextCallId, route ->
                    rootNav.navigate(Dest.Call.build(nextCallId, nextLeadId, route)) {
                        popUpTo(Dest.DispositionIncoming.route) { inclusive = true }
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
                onCampaign = { rootNav.navigate(Dest.CampaignDetail.build(it)) { launchSingleTop = true } },
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
                onOpenNotifications = { rootNav.navigate(Dest.Notifications.route) { launchSingleTop = true } },
                onOpenCampaigns = { rootNav.navigate(Dest.CampaignList.route) { launchSingleTop = true } },
                onOpenReports = { rootNav.navigate(Dest.Reports.route) { launchSingleTop = true } },
                onOpenSettings = { rootNav.navigate(Dest.Settings.route) { launchSingleTop = true } },
            )
        }
        composable(Dest.Leads.route) {
            LeadListScreen(
                onLeadClick = { rootNav.navigate(Dest.LeadDetail.build(it)) { launchSingleTop = true } },
                onStartCall = { leadId, callId, route ->
                    rootNav.navigate(Dest.Call.build(callId, leadId, route)) { launchSingleTop = true }
                },
            )
        }
        composable(Dest.Callbacks.route) {
            CallbackListScreen(
                onSchedule = { leadId ->
                    rootNav.navigate(Dest.ScheduleCallback.build(leadId)) { launchSingleTop = true }
                },
                onCall = { leadId, callId, route ->
                    rootNav.navigate(Dest.Call.build(callId, leadId, route)) { launchSingleTop = true }
                },
            )
        }
        composable(Dest.History.route) {
            CallHistoryScreen()
        }
        composable(Dest.Profile.route) {
            ProfileScreen(
                onEditProfile = { rootNav.navigate(Dest.EditProfile.route) { launchSingleTop = true } },
                onSettings = { rootNav.navigate(Dest.Settings.route) { launchSingleTop = true } },
                onReports = { rootNav.navigate(Dest.Reports.route) { launchSingleTop = true } },
                onHelp = { rootNav.navigate(Dest.HelpSupport.route) { launchSingleTop = true } },
                onTerms = { rootNav.navigate(Dest.TermsPrivacy.route) { launchSingleTop = true } },
                // No manual navigation here: ProfileViewModel.signOut() flips
                // authStatus → UNAUTHENTICATED, which the LaunchedEffect above turns
                // into a return-to-Login. Single source of truth.
                onSignOut = {},
            )
        }
    }
}

