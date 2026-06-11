package eu.kanade.tachiyomi.animeextension.en.rule34world

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal object Rule34WorldRequests {
    const val PAGE_SIZE = 30
    const val TYPE_VIDEO = 1
    const val STATUS_POSTED = 2
    const val SORT_LATEST = 0
    const val SORT_MOST_VIEWED = 2

    fun searchRequest(
        apiUrl: String,
        headers: Headers,
        json: Json,
        page: Int,
        params: Rule34WorldFilters.SearchParams,
    ): Request = when (params.mode) {
        Rule34WorldFilters.SearchMode.TAG_SEARCH -> rootSearchRequest(
            apiUrl = apiUrl,
            headers = headers,
            json = json,
            page = page,
            sortBy = params.sortBy,
            includeTags = params.includeTags,
        )
        Rule34WorldFilters.SearchMode.HOT -> hotSearchRequest(
            apiUrl = apiUrl,
            headers = headers,
            json = json,
            page = page,
            hotDays = params.hotDays,
        )
        Rule34WorldFilters.SearchMode.TOP_RATED -> rootSearchRequest(
            apiUrl = apiUrl,
            headers = headers,
            json = json,
            page = page,
            sortBy = params.sortBy,
            includeTags = null,
            postedFromDays = params.postedFromDays,
        )
    }

    fun rootSearchRequest(
        apiUrl: String,
        headers: Headers,
        json: Json,
        page: Int,
        sortBy: Int,
        includeTags: List<String>?,
        postedFromDays: Int? = null,
    ): Request {
        val data = buildJsonObject {
            put("take", PAGE_SIZE)
            put("skip", (page - 1).coerceAtLeast(0) * PAGE_SIZE)
            put("type", TYPE_VIDEO)
            put("sortBy", sortBy)
            put("withTags", true)
            includeTags?.let { tags ->
                putJsonArray("includeTags") {
                    tags.forEach { add(it) }
                }
            }
            postedFromDays?.let { put("postedFromDays", it) }
            put("status", STATUS_POSTED)
            put("checkHasMore", true)
        }

        return Request.Builder()
            .url("$apiUrl/post/search/root")
            .headers(headers)
            .post(data.toRequestBody(json))
            .build()
    }

    private fun hotSearchRequest(
        apiUrl: String,
        headers: Headers,
        json: Json,
        page: Int,
        hotDays: Int,
    ): Request {
        val data = buildJsonObject {
            put("take", PAGE_SIZE)
            put("skip", (page - 1).coerceAtLeast(0) * PAGE_SIZE)
            put("type", TYPE_VIDEO)
            put("withTags", true)
            put("status", STATUS_POSTED)
            put("checkHasMore", true)
        }

        return Request.Builder()
            .url("$apiUrl/post/search/hot/$hotDays")
            .headers(headers)
            .post(data.toRequestBody(json))
            .build()
    }

    private fun JsonObject.toRequestBody(json: Json) = json.encodeToString(JsonObject.serializer(), this).toRequestBody(JSON_MEDIA_TYPE)

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
}
