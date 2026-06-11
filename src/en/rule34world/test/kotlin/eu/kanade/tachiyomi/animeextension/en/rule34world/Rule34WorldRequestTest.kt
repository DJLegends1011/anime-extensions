package eu.kanade.tachiyomi.animeextension.en.rule34world

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.Request
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class Rule34WorldRequestTest {

    @Test
    fun `tag search mode posts root search with tags and selected sort`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.SearchSortFilter>().first().state = 2
            list.filterIsInstance<Rule34WorldFilters.TagsFilter>().first().state = "marvel, video"
        }

        val request = searchRequest(2, "emma frost", filters)
        val body = request.jsonBody()

        assertEquals("https://rule34.world/api/v2/post/search/root", request.url.toString())
        assertEquals(30, body["take"]!!.jsonPrimitive.int)
        assertEquals(30, body["skip"]!!.jsonPrimitive.int)
        assertEquals(1, body["type"]!!.jsonPrimitive.int)
        assertEquals(2, body["sortBy"]!!.jsonPrimitive.int)
        assertEquals(true, body["withTags"]!!.jsonPrimitive.boolean)
        assertEquals(
            listOf("emma frost", "marvel", "video"),
            body["includeTags"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        assertFalse(body.containsKey("postedFromDays"))
    }

    @Test
    fun `hot mode posts hot endpoint using selected site window`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 1
            list.filterIsInstance<Rule34WorldFilters.WindowFilter>().first().state = 0
            list.filterIsInstance<Rule34WorldFilters.TagsFilter>().first().state = "ignored"
        }

        val request = searchRequest(3, "ignored", filters)
        val body = request.jsonBody()

        assertEquals("https://rule34.world/api/v2/post/search/hot/10", request.url.toString())
        assertEquals(30, body["take"]!!.jsonPrimitive.int)
        assertEquals(60, body["skip"]!!.jsonPrimitive.int)
        assertEquals(1, body["type"]!!.jsonPrimitive.int)
        assertEquals(2, body["status"]!!.jsonPrimitive.int)
        assertEquals(true, body["withTags"]!!.jsonPrimitive.boolean)
        assertFalse(body.containsKey("sortBy"))
        assertFalse(body.containsKey("includeTags"))
    }

    @Test
    fun `hot mode falls back to weekly when all time is selected`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 1
            list.filterIsInstance<Rule34WorldFilters.WindowFilter>().first().state = 3
        }

        val request = searchRequest(1, "", filters)

        assertEquals("https://rule34.world/api/v2/post/search/hot/20", request.url.toString())
    }

    @Test
    fun `top rated mode posts root search using rating window`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 2
            list.filterIsInstance<Rule34WorldFilters.WindowFilter>().first().state = 1
        }

        val request = searchRequest(1, "", filters)
        val body = request.jsonBody()

        assertEquals("https://rule34.world/api/v2/post/search/root", request.url.toString())
        assertEquals(1, body["sortBy"]!!.jsonPrimitive.int)
        assertEquals(7, body["postedFromDays"]!!.jsonPrimitive.int)
        assertEquals(true, body["withTags"]!!.jsonPrimitive.boolean)
        assertFalse(body.containsKey("includeTags"))
    }

    @Test
    fun `top rated all time leaves postedFromDays out of request body`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 2
            list.filterIsInstance<Rule34WorldFilters.WindowFilter>().first().state = 3
        }

        val body = searchRequest(1, "", filters).jsonBody()

        assertEquals(1, body["sortBy"]!!.jsonPrimitive.int)
        assertFalse(body.containsKey("postedFromDays"))
    }

    private fun Request.jsonBody(): JsonObject {
        val buffer = Buffer()
        body!!.writeTo(buffer)
        return Json.parseToJsonElement(buffer.readUtf8()).jsonObject
    }

    private fun searchRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request = Rule34WorldRequests.searchRequest(
        apiUrl = "https://rule34.world/api/v2",
        headers = Headers.Builder().build(),
        json = Json,
        page = page,
        params = Rule34WorldFilters.getSearchParameters(query, filters),
    )
}
