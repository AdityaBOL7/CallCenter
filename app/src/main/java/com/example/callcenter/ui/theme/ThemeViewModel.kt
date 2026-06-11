package com.example.callcenter.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.callcenter.data.prefs.AppPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Exposes the persisted theme override so the whole app can react to changes live. */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    appPrefs: AppPreferences,
) : ViewModel() {
    val themeOverride = appPrefs.prefs
        .map { it.themeOverride }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "system")
}
