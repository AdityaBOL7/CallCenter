package com.example.callcenter.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Reads the TRUE talk duration of a just-placed SIM-mode call from the device
 * call log. This is the only accurate source for SIM calls: telephony state
 * (CallStateWatcher) reports OFFHOOK for both "ringing" and "connected", so it
 * can't tell when the other party actually picked up — the call log can. The OS
 * records DURATION = real answered seconds (and 0 for busy / missed / rejected,
 * i.e. nobody talked), which is exactly the number we want.
 *
 * Best-effort: returns null if READ_CALL_LOG isn't granted or no matching entry
 * is found (fresh device, delayed write, SD-card weirdness) — callers then fall
 * back to the approximate duration.
 */
object CallLogReader {

    data class Entry(val durationSec: Int, val type: Int)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Find the newest OUTGOING call-log entry to [number] whose call started at or
     * after [sinceEpochMillis] (minus a small slack), and return its real
     * duration. Returns null if permission is missing or nothing matches.
     *
     * Number matching is loose (compares the last 7+ digits) because the dialer
     * may store the number differently than we dialed it (country code, spaces).
     */
    fun latestOutgoingDuration(
        context: Context,
        number: String,
        sinceEpochMillis: Long,
    ): Entry? = latestEntry(
        context,
        number,
        sinceEpochMillis,
        types = intArrayOf(CallLog.Calls.OUTGOING_TYPE),
    )

    /**
     * Same as [latestOutgoingDuration] but for INBOUND entries (answered, missed
     * or rejected) — used to read the true talk time of an incoming SIM call
     * before reporting it to the backend. DURATION is the real answered seconds
     * (0 when nobody talked).
     */
    fun latestIncomingEntry(
        context: Context,
        number: String,
        sinceEpochMillis: Long,
    ): Entry? = latestEntry(
        context,
        number,
        sinceEpochMillis,
        types = intArrayOf(
            CallLog.Calls.INCOMING_TYPE,
            CallLog.Calls.MISSED_TYPE,
            CallLog.Calls.REJECTED_TYPE,
        ),
    )

    private fun latestEntry(
        context: Context,
        number: String,
        sinceEpochMillis: Long,
        types: IntArray,
    ): Entry? {
        if (!hasPermission(context)) return null
        val wantTail = number.filter { it.isDigit() }.takeLast(7)
        if (wantTail.isBlank()) return null

        // Look a little before the recorded start to absorb clock skew between our
        // timestamp and the call log's.
        val floor = sinceEpochMillis - 60_000L

        val typePlaceholders = types.joinToString(",") { "?" }
        return try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                ),
                // Calls of the wanted type(s) at/after our floor time.
                "${CallLog.Calls.TYPE} IN ($typePlaceholders) AND ${CallLog.Calls.DATE} >= ?",
                (types.map { it.toString() } + floor.toString()).toTypedArray(),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                val numIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
                val durIdx = c.getColumnIndexOrThrow(CallLog.Calls.DURATION)
                val typeIdx = c.getColumnIndexOrThrow(CallLog.Calls.TYPE)
                while (c.moveToNext()) {
                    val logged = c.getString(numIdx).orEmpty().filter { it.isDigit() }
                    if (logged.isNotEmpty() && logged.takeLast(7) == wantTail) {
                        val dur = c.getInt(durIdx)
                        val type = c.getInt(typeIdx)
                        Log.d(TAG, "call-log match: number=…$wantTail dur=${dur}s type=$type")
                        return Entry(durationSec = dur, type = type)
                    }
                }
                Log.d(TAG, "no call-log entry for …$wantTail since $floor")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "call-log read failed", e)
            null
        }
    }

    private const val TAG = "Bol7CallLog"
}
