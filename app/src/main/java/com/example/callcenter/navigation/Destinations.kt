package com.example.callcenter.navigation

sealed class Dest(val route: String) {
    // Animated brand splash shown on cold start.
    data object Splash : Dest("splash")

    // Auth
    data object Login : Dest("login")

    // Main scaffold host
    data object MainTabs : Dest("main")

    // Tab roots
    data object Dashboard : Dest("dashboard")
    data object Leads : Dest("leads")
    data object Callbacks : Dest("callbacks")
    data object History : Dest("history")
    data object Profile : Dest("profile")

    // Modal/stack
    data object LeadDetail : Dest("lead/{leadId}") {
        fun build(id: Int) = "lead/$id"
        const val ARG = "leadId"
    }
    data object Call : Dest("call/{callId}/{leadId}/{route}") {
        fun build(callId: String, leadId: Int, route: String) = "call/$callId/$leadId/$route"
        const val ARG_CALL = "callId"
        const val ARG_LEAD = "leadId"
        const val ARG_ROUTE = "route"
    }
    data object Disposition : Dest("disposition/{callId}/{leadId}") {
        fun build(callId: String, leadId: Int) = "disposition/$callId/$leadId"
        const val ARG_CALL = "callId"
        const val ARG_LEAD = "leadId"
    }

    // Disposition for an answered INCOMING SIM call. No args: there's no backend
    // call/lead yet (the sim-incoming POST is deferred to submit/cancel), so the
    // screen reads the caller's number + talk time from the persisted pending
    // incoming call.
    data object DispositionIncoming : Dest("dispositionIncoming")
    data object ScheduleCallback : Dest("schedule/{leadId}") {
        fun build(leadId: Int) = "schedule/$leadId"
        const val ARG = "leadId"
    }
    data object CampaignList : Dest("campaigns")
    data object CampaignDetail : Dest("campaign/{id}") {
        fun build(id: Int) = "campaign/$id"
        const val ARG = "id"
    }
    data object Notifications : Dest("notifications")
    data object Settings : Dest("settings")
    data object EditProfile : Dest("editProfile")
    data object HelpSupport : Dest("help")
    data object TermsPrivacy : Dest("terms")
    data object Reports : Dest("reports")
}
