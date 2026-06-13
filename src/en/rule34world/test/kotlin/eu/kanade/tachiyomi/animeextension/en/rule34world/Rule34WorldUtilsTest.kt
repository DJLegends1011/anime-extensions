package eu.kanade.tachiyomi.animeextension.en.rule34world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Rule34WorldUtilsTest {

    @Test
    fun `parseSearchTags splits comma separated tags and trims blanks`() {
        assertEquals(
            listOf("animated", "loop", "sound"),
            Rule34WorldUtils.parseSearchTags(" animated, loop ,, sound "),
        )
    }

    @Test
    fun `parseSearchTags keeps spaces inside a single tag`() {
        assertEquals(
            listOf("artist name"),
            Rule34WorldUtils.parseSearchTags("artist name"),
        )
    }

    @Test
    fun `postIdFromUrl accepts only rule34world post urls`() {
        assertEquals(1338481L, Rule34WorldUtils.postIdFromUrl("https://rule34.world/post/1338481"))
        assertEquals(1338481L, Rule34WorldUtils.postIdFromUrl("https://rule34.world/post/1338481/"))
        assertNull(Rule34WorldUtils.postIdFromUrl("https://rule34.world/user/1338481"))
        assertNull(Rule34WorldUtils.postIdFromUrl("https://example.com/post/1338481"))
        assertNull(Rule34WorldUtils.postIdFromUrl("storm (marvel rivals)"))
    }

    @Test
    fun `title uses stable post id instead of tags`() {
        assertEquals(
            "Post #1339948",
            Rule34WorldUtils.title(
                Rule34WorldPost(
                    id = 1339948,
                    duration = 27,
                    width = 1920,
                    height = 1080,
                    tags = listOf(
                        Rule34WorldTag(value = "storm (marvel rivals)", type = 4),
                        Rule34WorldTag(value = "marvel rivals", type = 2),
                    ),
                ),
            ),
        )
    }

    @Test
    fun `mediaUrl prefers direct file url`() {
        assertEquals(
            "https://cdn.example/video.mp4",
            Rule34WorldUtils.mediaUrl(
                postId = 1,
                fileId = "113",
                files = emptyMap(),
                filesDirect = mapOf("113" to "https://cdn.example/video.mp4"),
            ),
        )
    }

    @Test
    fun `mediaUrl uses storage cdn when file is on storage two`() {
        assertEquals(
            "https://rule34storage.b-cdn.net/posts/1337/1337088/1337088.mov720.mp4",
            Rule34WorldUtils.mediaUrl(
                postId = 1337088,
                fileId = "113",
                files = mapOf("113" to listOf(0, 2)),
                filesDirect = emptyMap(),
            ),
        )
    }

    @Test
    fun `mediaUrl falls back to site host when storage two is absent`() {
        assertEquals(
            "https://rule34.world/posts/1337/1337096/1337096.mov720.mp4",
            Rule34WorldUtils.mediaUrl(
                postId = 1337096,
                fileId = "113",
                files = mapOf("113" to listOf(0)),
                filesDirect = emptyMap(),
            ),
        )
    }

    @Test
    fun `formatGroupedTagsForDescription groups tags by site tag type order`() {
        val tags = listOf(
            Rule34WorldTag(value = "video", type = 1),
            Rule34WorldTag(value = "merida", type = 4),
            Rule34WorldTag(value = "brave", type = 2),
            Rule34WorldTag(value = "4:3", type = 32),
            Rule34WorldTag(value = "kae-est art", type = 8),
            Rule34WorldTag(value = "1girls", type = 1),
            Rule34WorldTag(value = "pixar", type = 2),
        )

        assertEquals(
            """
            Copyright: brave, pixar
            Character: merida
            Artist: kae-est art
            Resolution: 4:3
            General: 1girls, video
            """.trimIndent(),
            Rule34WorldUtils.formatGroupedTagsForDescription(tags),
        )
    }

    @Test
    fun `formatGroupedTagsForDescription separates best of system tags`() {
        val tags = listOf(
            Rule34WorldTag(value = "video", type = 1),
            Rule34WorldTag(value = "poppy playtime", type = 2),
            Rule34WorldTag(value = "muffinpad", type = 8),
            Rule34WorldTag(value = "best of the week", type = 16),
            Rule34WorldTag(value = "best of the day", type = 16),
            Rule34WorldTag(value = "1boy", type = 1),
        )

        assertEquals(
            """
            Copyright: poppy playtime
            Artist: muffinpad
            Best Of: best of the day, best of the week
            General: 1boy, video
            """.trimIndent(),
            Rule34WorldUtils.formatGroupedTagsForDescription(tags),
        )
    }

    @Test
    fun `formatTagsForGenre keeps individual clickable tags ordered by site tag groups`() {
        val tags = listOf(
            Rule34WorldTag(value = "video", type = 1),
            Rule34WorldTag(value = "merida", type = 4),
            Rule34WorldTag(value = "brave", type = 2),
            Rule34WorldTag(value = "4:3", type = 32),
            Rule34WorldTag(value = "kae-est art", type = 8),
            Rule34WorldTag(value = "1girls", type = 1),
            Rule34WorldTag(value = "pixar", type = 2),
        )

        assertEquals(
            "brave, pixar, merida, kae-est art, 4:3, 1girls, video",
            Rule34WorldUtils.formatTagsForGenre(tags),
        )
    }

    @Test
    fun `formatTagsForGenre keeps best of system tags before general tags`() {
        val tags = listOf(
            Rule34WorldTag(value = "video", type = 1),
            Rule34WorldTag(value = "best of the week", type = 16),
            Rule34WorldTag(value = "best of the day", type = 16),
            Rule34WorldTag(value = "1boy", type = 1),
        )

        assertEquals(
            "best of the day, best of the week, 1boy, video",
            Rule34WorldUtils.formatTagsForGenre(tags),
        )
    }

    @Test
    fun `filterBlockedPosts removes posts with blacklisted tags case insensitively`() {
        val posts = listOf(
            Rule34WorldPost(
                id = 1,
                tags = listOf(Rule34WorldTag(value = "Raven (Teen Titans)", type = 4)),
            ),
            Rule34WorldPost(
                id = 2,
                tags = listOf(Rule34WorldTag(value = "starfire", type = 4)),
            ),
        )

        assertEquals(
            listOf(2L),
            Rule34WorldUtils.filterBlockedPosts(posts, listOf("raven (teen titans)")).map { it.id },
        )
    }

    @Test
    fun `filterSuggestionPosts skips current blocked and non posted videos then caps results`() {
        val posts = listOf(
            Rule34WorldPost(
                id = 1,
                type = 1,
                status = 2,
                tags = listOf(Rule34WorldTag(value = "keep")),
            ),
            Rule34WorldPost(
                id = 2,
                type = 1,
                status = 2,
                tags = listOf(Rule34WorldTag(value = "blocked")),
            ),
            Rule34WorldPost(
                id = 3,
                type = 0,
                status = 2,
                tags = listOf(Rule34WorldTag(value = "keep")),
            ),
            Rule34WorldPost(
                id = 4,
                type = 1,
                status = 1,
                tags = listOf(Rule34WorldTag(value = "keep")),
            ),
            Rule34WorldPost(
                id = 5,
                type = 1,
                status = 2,
                tags = listOf(Rule34WorldTag(value = "keep")),
            ),
            Rule34WorldPost(
                id = 6,
                type = 1,
                status = 2,
                tags = listOf(Rule34WorldTag(value = "keep")),
            ),
        )

        assertEquals(
            listOf(5L, 6L),
            Rule34WorldUtils.filterSuggestionPosts(
                posts = posts,
                currentPostId = 1,
                blockedTags = listOf("blocked"),
                limit = 2,
            ).map { it.id },
        )
    }

    @Test
    fun `mergeSuggestionPosts caps each priority group and keeps later groups represented`() {
        val artistBatch = listOf(
            suggestionPost(2),
            suggestionPost(3),
            suggestionPost(4),
        )
        val characterBatch = listOf(
            suggestionPost(3),
            suggestionPost(5),
            suggestionPost(6),
        )

        assertEquals(
            listOf(2L, 3L, 5L, 6L),
            Rule34WorldUtils.mergeSuggestionPosts(
                postGroups = listOf(artistBatch, characterBatch),
                currentPostId = 1,
                blockedTags = emptyList(),
                perGroupLimit = 2,
                totalLimit = 4,
            ).map { it.id },
        )
    }

    @Test
    fun `mergeSuggestionPosts ranks stronger prioritized tag matches before weak fallbacks`() {
        val characterBatch = listOf(
            suggestionPost(2, "storm (marvel rivals)"),
            suggestionPost(3, "storm (marvel rivals)", "marvel rivals"),
        )
        val artistBatch = listOf(
            suggestionPost(4, "vicki foxxynsfw"),
        )

        assertEquals(
            listOf(3L, 2L, 4L),
            Rule34WorldUtils.mergeSuggestionPosts(
                postGroups = listOf(characterBatch, artistBatch),
                currentPostId = 1,
                blockedTags = emptyList(),
                perGroupLimit = 2,
                totalLimit = 3,
                priorityTags = listOf("storm (marvel rivals)", "marvel rivals", "vicki foxxynsfw"),
            ).map { it.id },
        )
    }

    @Test
    fun `suggestionSearchTagGroups prioritizes character artist copyright and skips general tags`() {
        val tags = listOf(
            Rule34WorldTag(value = "video", count = 179287, type = 1),
            Rule34WorldTag(value = "animated", count = 169582, type = 1),
            Rule34WorldTag(value = "blowjob", count = 65061, type = 1),
            Rule34WorldTag(value = "green skin", count = 8845, type = 1),
            Rule34WorldTag(value = "teen titans", count = 821, type = 2),
            Rule34WorldTag(value = "raven (teen titans)", count = 127, type = 4),
            Rule34WorldTag(value = "raven (dc)", count = 6992, type = 4),
            Rule34WorldTag(value = "chocolatepete0", count = 22, type = 8),
        )

        assertEquals(
            listOf(
                listOf("raven (teen titans)", "raven (dc)"),
                listOf("chocolatepete0"),
                listOf("teen titans"),
            ),
            Rule34WorldUtils.suggestionSearchTagGroups(tags).map { it.tags },
        )
    }

    @Test
    fun `suggestionSearchTags flattens priority groups for search ordering`() {
        val tags = listOf(
            Rule34WorldTag(value = "sex", count = 249353, type = 1),
            Rule34WorldTag(value = "invincible", count = 1604, type = 2),
            Rule34WorldTag(value = "anissa (invincible)", count = 59, type = 4),
            Rule34WorldTag(value = "fairykissva", count = 45, type = 8),
        )

        assertEquals(
            listOf("anissa (invincible)", "fairykissva", "invincible"),
            Rule34WorldUtils.suggestionSearchTags(tags),
        )
    }

    @Test
    fun `suggestionSearchBatches searches combined group before individual fallbacks`() {
        assertEquals(
            listOf(
                listOf("storm (marvel rivals)", "storm (x-men)", "ororo munroe"),
                listOf("storm (marvel rivals)"),
                listOf("storm (x-men)"),
                listOf("ororo munroe"),
            ),
            Rule34WorldUtils.suggestionSearchBatches(
                listOf("storm (marvel rivals)", "storm (x-men)", "ororo munroe"),
            ),
        )

        assertEquals(
            listOf(listOf("fpsblyck")),
            Rule34WorldUtils.suggestionSearchBatches(listOf("fpsblyck")),
        )
    }

    private fun suggestionPost(id: Long, vararg tags: String): Rule34WorldPost = Rule34WorldPost(
        id = id,
        type = 1,
        status = 2,
        tags = (tags.takeIf { it.isNotEmpty() } ?: arrayOf("keep"))
            .map { Rule34WorldTag(value = it) },
    )
}
