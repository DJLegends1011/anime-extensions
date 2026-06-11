package eu.kanade.tachiyomi.animeextension.en.rule34world

import kotlinx.serialization.Serializable

@Serializable
internal data class Rule34WorldSearchResponse(
    val items: List<Rule34WorldPost> = emptyList(),
    val cursor: String? = null,
    val hasMore: Boolean = false,
    val pagination: Int? = null,
)

@Serializable
internal data class Rule34WorldPost(
    val id: Long,
    val created: String? = null,
    val posted: String? = null,
    val likes: Int? = null,
    val views: Int? = null,
    val type: Int? = null,
    val status: Int? = null,
    val uploaderId: Long? = null,
    val duration: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
    val files: Map<String, List<Int>> = emptyMap(),
    val filesDirect: Map<String, String> = emptyMap(),
    val tags: List<Rule34WorldTag> = emptyList(),
    val data: Rule34WorldPostData? = null,
)

@Serializable
internal data class Rule34WorldTag(
    val id: Long? = null,
    val value: String = "",
    val count: Int? = null,
    val popularity: Int? = null,
    val type: Int? = null,
)

@Serializable
internal data class Rule34WorldPostData(
    val sources: List<String> = emptyList(),
)
