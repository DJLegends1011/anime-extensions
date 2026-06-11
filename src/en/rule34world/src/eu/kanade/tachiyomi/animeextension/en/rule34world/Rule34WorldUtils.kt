package eu.kanade.tachiyomi.animeextension.en.rule34world

internal object Rule34WorldUtils {
    private const val SITE_BASE_URL = "https://rule34.world"
    private const val STORAGE_BASE_URL = "https://rule34storage.b-cdn.net"
    private const val STORAGE_ID_BUNNY = 2
    private val aspectRatioRegex = Regex("""^\d+(?:\.\d+)?:\d+(?:\.\d+)?$""")

    private val fileParts = mapOf(
        "10" to "pic.jpg",
        "13" to "picpreview.jpg",
        "14" to "picsmall.jpg",
        "100" to "mov.mp4",
        "112" to "mov480.mp4",
        "113" to "mov720.mp4",
        "114" to "1080.mp4",
    )

    val videoFiles = listOf(
        Rule34WorldFile("114", "1080p"),
        Rule34WorldFile("113", "720p"),
        Rule34WorldFile("112", "480p"),
        Rule34WorldFile("100", "Original"),
    )

    fun parseSearchTags(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return emptyList()

        return if (trimmed.contains(",")) {
            trimmed.split(",")
                .map(String::trim)
                .filter(String::isNotBlank)
        } else {
            listOf(trimmed)
        }
    }

    fun mediaUrl(
        postId: Long,
        fileId: String,
        files: Map<String, List<Int>>,
        filesDirect: Map<String, String>,
    ): String {
        filesDirect[fileId]?.takeIf(String::isNotBlank)?.let { return it }

        val filePart = fileParts.getValue(fileId)
        val host = if (files[fileId]?.contains(STORAGE_ID_BUNNY) == true) {
            STORAGE_BASE_URL
        } else {
            SITE_BASE_URL
        }

        return "$host/posts/${postId / 1000}/$postId/$postId.$filePart"
    }

    fun availableVideos(
        files: Map<String, List<Int>>,
        filesDirect: Map<String, String>,
    ): List<Rule34WorldFile> = videoFiles.filter { file ->
        files.containsKey(file.id) || filesDirect[file.id]?.isNotBlank() == true
    }

    fun thumbnailUrl(post: Rule34WorldPost): String? = listOf("13", "14", "10")
        .firstOrNull { post.files.containsKey(it) || post.filesDirect[it]?.isNotBlank() == true }
        ?.let { mediaUrl(post.id, it, post.files, post.filesDirect) }

    fun title(post: Rule34WorldPost): String {
        val tags = post.tags.mapNotNull { it.value.takeIf(String::isNotBlank) }
        if (tags.isNotEmpty()) return tags.take(5).joinToString(" ")

        return buildString {
            append("Post #")
            append(post.id)
            post.duration?.let { append(" (${it}s)") }
            if (post.width != null && post.height != null) {
                append(" - ")
                append(post.width)
                append("x")
                append(post.height)
            }
        }
    }

    fun formatGroupedTagsForDescription(tags: List<Rule34WorldTag>): String = groupedTags(tags)
        .joinToString("\n") { group ->
            "${group.label}: ${group.tags.joinToString(", ")}"
        }

    fun formatTagsForGenre(tags: List<Rule34WorldTag>): String = groupedTags(tags)
        .flatMap { it.tags }
        .joinToString()

    fun filterBlockedPosts(posts: List<Rule34WorldPost>, blockedTags: List<String>): List<Rule34WorldPost> {
        val normalizedBlockedTags = blockedTags.mapNotNull(::normalizedTag).toSet()
        if (normalizedBlockedTags.isEmpty()) return posts

        return posts.filterNot { post ->
            post.tags.any { tag -> normalizedTag(tag.value) in normalizedBlockedTags }
        }
    }

    fun filterSuggestionPosts(
        posts: List<Rule34WorldPost>,
        currentPostId: Long,
        blockedTags: List<String>,
        limit: Int,
    ): List<Rule34WorldPost> = filterBlockedPosts(posts, blockedTags)
        .asSequence()
        .filter { it.id != currentPostId }
        .filter { it.type == Rule34WorldRequests.TYPE_VIDEO && it.status == Rule34WorldRequests.STATUS_POSTED }
        .take(limit.coerceAtLeast(0))
        .toList()

    fun mergeSuggestionPosts(
        postGroups: List<List<Rule34WorldPost>>,
        currentPostId: Long,
        blockedTags: List<String>,
        perGroupLimit: Int,
        totalLimit: Int,
    ): List<Rule34WorldPost> {
        val cappedTotalLimit = totalLimit.coerceAtLeast(0)
        val cappedPerGroupLimit = perGroupLimit.coerceAtLeast(0)
        val suggestions = linkedMapOf<Long, Rule34WorldPost>()

        postGroups.forEach { posts ->
            if (suggestions.size >= cappedTotalLimit) return@forEach
            if (cappedPerGroupLimit == 0) return@forEach

            var addedFromGroup = 0
            for (post in filterBlockedPosts(posts, blockedTags)) {
                if (addedFromGroup >= cappedPerGroupLimit || suggestions.size >= cappedTotalLimit) break
                if (post.id == currentPostId) continue
                if (post.type != Rule34WorldRequests.TYPE_VIDEO || post.status != Rule34WorldRequests.STATUS_POSTED) continue
                if (suggestions.containsKey(post.id)) continue

                suggestions[post.id] = post
                addedFromGroup++
            }
        }

        return suggestions.values.toList()
    }

    fun suggestionSearchTagGroups(tags: List<Rule34WorldTag>): List<Rule34WorldSuggestionTagGroup> {
        val seen = mutableSetOf<String>()

        return suggestionTagTypeOrder.mapNotNull { type ->
            tags.asSequence()
                .filter { it.type == type }
                .mapNotNull { tag ->
                    val value = tag.value.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val normalized = normalizedTag(value) ?: return@mapNotNull null
                    if (type == TAG_TYPE_GENERAL && normalized in broadSuggestionTags) return@mapNotNull null
                    Rule34WorldSuggestionTag(value, normalized, tag.count)
                }
                .sortedWith(
                    compareBy<Rule34WorldSuggestionTag> { it.count ?: Int.MAX_VALUE }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.value },
                )
                .filter { seen.add(it.normalized) }
                .map { it.value }
                .toList()
                .takeIf(List<String>::isNotEmpty)
                ?.let(::Rule34WorldSuggestionTagGroup)
        }
    }

    fun suggestionSearchTags(tags: List<Rule34WorldTag>): List<String> = suggestionSearchTagGroups(tags).flatMap { it.tags }

    private fun groupedTags(tags: List<Rule34WorldTag>): List<Rule34WorldTagGroup> {
        val grouped = tags
            .asSequence()
            .mapNotNull { tag ->
                val value = tag.value.trim().takeIf(String::isNotBlank) ?: return@mapNotNull null
                tag.toGroupKey(value) to value
            }
            .distinct()
            .groupBy({ it.first }, { it.second })

        return tagGroupOrder.mapNotNull { definition ->
            grouped[definition.key]
                ?.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
                ?.takeIf(List<String>::isNotEmpty)
                ?.let { Rule34WorldTagGroup(definition.label, it) }
        }
    }

    private fun Rule34WorldTag.toGroupKey(value: String): String = when (type) {
        TAG_TYPE_COPYRIGHT -> GROUP_COPYRIGHT
        TAG_TYPE_CHARACTER -> GROUP_CHARACTER
        TAG_TYPE_ARTIST -> GROUP_ARTIST
        TAG_TYPE_META -> if (aspectRatioRegex.matches(value)) GROUP_RESOLUTION else GROUP_META
        TAG_TYPE_SYSTEM -> if (value.startsWith(BEST_OF_PREFIX, ignoreCase = true)) GROUP_BEST_OF else GROUP_SYSTEM
        TAG_TYPE_GENERAL, null -> GROUP_GENERAL
        else -> GROUP_GENERAL
    }

    private fun normalizedTag(value: String): String? = value.trim()
        .lowercase()
        .takeIf(String::isNotBlank)

    private const val TAG_TYPE_GENERAL = 1
    private const val TAG_TYPE_COPYRIGHT = 2
    private const val TAG_TYPE_CHARACTER = 4
    private const val TAG_TYPE_ARTIST = 8
    private const val TAG_TYPE_SYSTEM = 16
    private const val TAG_TYPE_META = 32
    private const val BEST_OF_PREFIX = "best of "

    private const val GROUP_COPYRIGHT = "copyright"
    private const val GROUP_CHARACTER = "character"
    private const val GROUP_ARTIST = "artist"
    private const val GROUP_RESOLUTION = "resolution"
    private const val GROUP_BEST_OF = "best_of"
    private const val GROUP_GENERAL = "general"
    private const val GROUP_META = "meta"
    private const val GROUP_SYSTEM = "system"

    private val tagGroupOrder = listOf(
        Rule34WorldTagGroupDefinition(GROUP_COPYRIGHT, "Copyright"),
        Rule34WorldTagGroupDefinition(GROUP_CHARACTER, "Character"),
        Rule34WorldTagGroupDefinition(GROUP_ARTIST, "Artist"),
        Rule34WorldTagGroupDefinition(GROUP_RESOLUTION, "Resolution"),
        Rule34WorldTagGroupDefinition(GROUP_BEST_OF, "Best Of"),
        Rule34WorldTagGroupDefinition(GROUP_GENERAL, "General"),
        Rule34WorldTagGroupDefinition(GROUP_META, "Meta"),
        Rule34WorldTagGroupDefinition(GROUP_SYSTEM, "System"),
    )

    private val suggestionTagTypeOrder = listOf(
        TAG_TYPE_CHARACTER,
        TAG_TYPE_ARTIST,
        TAG_TYPE_COPYRIGHT,
    )

    private val broadSuggestionTags = setOf(
        "animated",
        "tagme",
        "video",
    )
}

internal data class Rule34WorldFile(
    val id: String,
    val quality: String,
)

private data class Rule34WorldTagGroup(
    val label: String,
    val tags: List<String>,
)

private data class Rule34WorldTagGroupDefinition(
    val key: String,
    val label: String,
)

private data class Rule34WorldSuggestionTag(
    val value: String,
    val normalized: String,
    val count: Int?,
)

internal data class Rule34WorldSuggestionTagGroup(
    val tags: List<String>,
)
