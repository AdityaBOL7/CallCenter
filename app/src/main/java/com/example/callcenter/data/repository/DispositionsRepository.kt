package com.example.callcenter.data.repository

import android.util.Log
import com.example.callcenter.data.remote.api.DialerApi
import com.example.callcenter.data.remote.dto.DispositionDto
import com.example.callcenter.domain.model.Disposition
import com.example.callcenter.domain.model.DispositionCode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Singleton
class DispositionsRepository @Inject constructor(
    private val dialerApi: DialerApi,
    private val json: Json,
) {

    /** Built-in codes, used until the backend list loads (or if it's empty). */
    private val fallback: List<DispositionCode> = Disposition.entries.map {
        DispositionCode(code = it.name.lowercase(), label = it.label, mapped = it)
    }

    private val _codes = MutableStateFlow(fallback)
    val codes: StateFlow<List<DispositionCode>> = _codes.asStateFlow()

    suspend fun refresh(): Result<Unit> {
        return try {
            val element = dialerApi.listDispositions()
            val dtos = parseList(element).filter { it.isActive != false }
            Log.d(TAG, "dispositions/ ← ${dtos.size} codes")
            val mapped = dtos
                .sortedBy { it.sortOrder ?: Int.MAX_VALUE }
                .mapNotNull { it.toCodeOrNull() }
            // Keep the built-in set if the backend returns nothing usable.
            _codes.value = mapped.ifEmpty { fallback }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "dispositions/ failed — keeping fallback", e)
            Result.failure(e)
        }
    }

    private fun parseList(element: JsonElement): List<DispositionDto> = when (element) {
        is JsonArray -> json.decodeFromJsonElement(ListSerializer(DispositionDto.serializer()), element)
        is JsonObject -> {
            val results = element["results"]
            if (results is JsonArray) {
                json.decodeFromJsonElement(ListSerializer(DispositionDto.serializer()), results)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }

    private fun DispositionDto.toCodeOrNull(): DispositionCode? {
        val label = resolvedLabel ?: return null
        val codeKey = code?.takeIf { it.isNotBlank() } ?: label
        return DispositionCode(code = codeKey, label = label, mapped = mapToEnum(codeKey, label))
    }

    /** Best-effort match of a dynamic code/label onto the built-in enum. */
    private fun mapToEnum(code: String, label: String): Disposition {
        val key = (code + " " + label).lowercase()
        return when {
            "convert" in key -> Disposition.CONVERTED
            "not" in key && "interest" in key -> Disposition.NOT_INTERESTED
            "interest" in key -> Disposition.INTERESTED
            "follow" in key || "callback" in key || "call back" in key -> Disposition.FOLLOW_UP
            "wrong" in key -> Disposition.WRONG_NUMBER
            "busy" in key -> Disposition.BUSY
            "no answer" in key || "noanswer" in key || "no-answer" in key -> Disposition.NO_ANSWER
            "clos" in key || "dnc" in key || "do not" in key -> Disposition.CLOSED
            else -> Disposition.FOLLOW_UP
        }
    }

    private companion object {
        const val TAG = "Bol7Disp"
    }
}
