package com.example.callcenter.ui.components

import android.net.Uri
import androidx.compose.runtime.saveable.Saver
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Savers for `rememberSaveable` over types the framework can't auto-bundle, so
 * form state on the Disposition / Schedule-Callback screens survives rotation
 * and process death.
 *
 * [LocalDateTime] is stored as its ISO-8601 string (null → empty); [Uri] as its
 * string form (null → empty). Both round-trip exactly.
 */
val LocalDateTimeSaver: Saver<LocalDateTime?, String> = Saver(
    save = { it?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "" },
    restore = { s -> s.takeIf { it.isNotEmpty() }?.let(LocalDateTime::parse) },
)

val UriSaver: Saver<Uri?, String> = Saver(
    save = { it?.toString() ?: "" },
    restore = { s -> s.takeIf { it.isNotEmpty() }?.let(Uri::parse) },
)
