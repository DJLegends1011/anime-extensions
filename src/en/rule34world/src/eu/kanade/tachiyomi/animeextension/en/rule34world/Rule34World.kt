package eu.kanade.tachiyomi.animeextension.en.rule34world

import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response

class Rule34World :
    AnimeHttpSource(),
    ConfigurableAnimeSource {

    override val name = "Rule34World"

    override val baseUrl = "https://rule34.world"

    override val lang = "en"

    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api/v2"

    private val preferences by getPreferencesLazy()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request = searchRequest(
        page = page,
        sortBy = Rule34WorldRequests.SORT_MOST_VIEWED,
        includeTags = emptyList(),
    )

    override fun popularAnimeParse(response: Response): AnimesPage = parsePosts(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchRequest(
        page = page,
        sortBy = Rule34WorldRequests.SORT_LATEST,
        includeTags = emptyList(),
    )

    override fun latestUpdatesParse(response: Response): AnimesPage = parsePosts(response)

    // =============================== Search ===============================

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        postIdFromUrl(query)?.let { id ->
            val post = client.newCall(GET("$apiUrl/post/$id", headers))
                .execute()
                .parseAs<Rule34WorldPost>(json)

            return AnimesPage(listOf(post.toSAnime()), false)
        }

        return super.getSearchAnime(page, query, filters)
    }

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        val params = Rule34WorldFilters.getSearchParameters(query, filters)
        return Rule34WorldRequests.searchRequest(apiUrl, headers, json, page, params)
    }

    override fun searchAnimeParse(response: Response): AnimesPage = parsePosts(response)

    override fun getFilterList(): AnimeFilterList = Rule34WorldFilters.FILTER_LIST

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request = GET("$apiUrl/post/${anime.url}", headers)

    override fun animeDetailsParse(response: Response): SAnime = response
        .parseAs<Rule34WorldPost>(json)
        .toSAnime(includeDetails = true)

    override fun getAnimeUrl(anime: SAnime): String = "$baseUrl/post/${anime.url}"

    // ============================== Related ===============================

    override suspend fun fetchRelatedAnimeList(anime: SAnime): List<SAnime> {
        val currentPost = client.newCall(animeDetailsRequest(anime))
            .execute()
            .use { response -> response.parseAs<Rule34WorldPost>(json) }
        val blockedTags = blacklistTags()
        val postGroups = mutableListOf<List<Rule34WorldPost>>()
        val tagGroups = Rule34WorldUtils.suggestionSearchTagGroups(currentPost.tags)
            .map { group -> group.tags.take(SUGGESTION_TAGS_PER_GROUP_LIMIT) }
            .filter(List<String>::isNotEmpty)
        val perGroupLimit = suggestionLimitPerGroup(tagGroups.size)

        for (tags in tagGroups) {
            val groupPosts = linkedMapOf<Long, Rule34WorldPost>()

            for (tag in tags) {
                client.newCall(searchRequest(1, Rule34WorldRequests.SORT_LATEST, listOf(tag)))
                    .execute()
                    .use { searchResponse ->
                        val parsed = searchResponse.parseAs<Rule34WorldSearchResponse>(json)
                        parsed.items.forEach { post -> groupPosts.putIfAbsent(post.id, post) }
                    }
            }

            postGroups += groupPosts.values.toList()
        }

        return Rule34WorldUtils.mergeSuggestionPosts(
            postGroups = postGroups,
            currentPostId = currentPost.id,
            blockedTags = blockedTags,
            perGroupLimit = perGroupLimit,
            totalLimit = SUGGESTION_LIMIT,
        ).map { it.toSAnime() }
    }

    // ============================== Episodes ==============================

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> = listOf(
        SEpisode.create().apply {
            url = anime.url
            name = anime.title.takeIf(String::isNotBlank) ?: "Video"
            episode_number = 1F
        },
    )

    override fun episodeListParse(response: Response): List<SEpisode> = throw UnsupportedOperationException()

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request = GET("$apiUrl/post/${episode.url}", headers)

    override fun videoListParse(response: Response): List<Video> {
        val post = response.parseAs<Rule34WorldPost>(json)
        val videoHeaders = headersBuilder()
            .set("Accept", "video/mp4,video/*;q=0.9,*/*;q=0.8")
            .set("Referer", "$baseUrl/post/${post.id}")
            .build()

        return Rule34WorldUtils.availableVideos(post.files, post.filesDirect).map { file ->
            val url = Rule34WorldUtils.mediaUrl(post.id, file.id, post.files, post.filesDirect)
            Video(url, file.quality, url, videoHeaders)
        }
    }

    // ============================== Settings ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_BLACKLIST_TAGS
            title = "Blacklist tags"
            summary = "Comma-separated tags to hide from browsing and suggestions."
            dialogTitle = "Blacklist tags"
            setDefaultValue("")
        }.also(screen::addPreference)
    }

    private fun searchRequest(page: Int, sortBy: Int, includeTags: List<String>): Request = Rule34WorldRequests.rootSearchRequest(apiUrl, headers, json, page, sortBy, includeTags)

    private fun parsePosts(response: Response): AnimesPage {
        val parsed = response.parseAs<Rule34WorldSearchResponse>(json)
        val animes = Rule34WorldUtils.filterBlockedPosts(parsed.items, blacklistTags())
            .filter { it.isPostedVideo() }
            .map { it.toSAnime() }

        return AnimesPage(animes, parsed.hasMore)
    }

    private fun Rule34WorldPost.toSAnime(includeDetails: Boolean = false): SAnime = SAnime.create().apply {
        url = id.toString()
        title = Rule34WorldUtils.title(this@toSAnime)
        thumbnail_url = Rule34WorldUtils.thumbnailUrl(this@toSAnime)
        status = SAnime.COMPLETED

        if (includeDetails) {
            genre = Rule34WorldUtils.formatTagsForGenre(tags)
            author = uploaderId?.let { "Uploader #$it" }
            description = buildDescription()
        }
    }

    private fun Rule34WorldPost.buildDescription(): String = buildString {
        posted?.let { appendLine("Posted: $it") }
        created?.let { appendLine("Created: $it") }
        duration?.let { appendLine("Duration: ${it}s") }
        if (width != null && height != null) appendLine("Resolution: ${width}x$height")
        likes?.let { appendLine("Likes: $it") }
        views?.let { appendLine("Views: $it") }
        uploaderId?.let { appendLine("Uploader: #$it") }

        data?.sources
            ?.filter(String::isNotBlank)
            ?.takeIf(List<String>::isNotEmpty)
            ?.let { sources ->
                appendLine()
                appendLine("Sources:")
                sources.forEach { appendLine(it) }
            }
    }.trim()

    private fun postIdFromUrl(query: String): Long? {
        val url = query.toHttpUrlOrNull() ?: return null
        if (url.host != "rule34.world") return null
        return url.pathSegments
            .lastOrNull()
            ?.toLongOrNull()
    }

    private fun blacklistTags(): List<String> = Rule34WorldUtils.parseSearchTags(
        preferences.getString(PREF_BLACKLIST_TAGS, "").orEmpty(),
    )

    private fun Rule34WorldPost.isPostedVideo(): Boolean = type == Rule34WorldRequests.TYPE_VIDEO &&
        status == Rule34WorldRequests.STATUS_POSTED

    private fun suggestionLimitPerGroup(groupCount: Int): Int {
        if (groupCount <= 0) return SUGGESTION_LIMIT
        return (SUGGESTION_LIMIT + groupCount - 1) / groupCount
    }

    private companion object {
        const val PREF_BLACKLIST_TAGS = "blacklist_tags"
        const val SUGGESTION_LIMIT = 30
        const val SUGGESTION_TAGS_PER_GROUP_LIMIT = 4
    }
}
