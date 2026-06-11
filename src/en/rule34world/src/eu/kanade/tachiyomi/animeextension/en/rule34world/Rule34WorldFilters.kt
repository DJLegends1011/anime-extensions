package eu.kanade.tachiyomi.animeextension.en.rule34world

import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList

internal object Rule34WorldFilters {
    enum class SearchMode {
        TAG_SEARCH,
        HOT,
        TOP_RATED,
    }

    private val modeOptions = arrayOf(
        Pair("Tag Search", SearchMode.TAG_SEARCH),
        Pair("Hot", SearchMode.HOT),
        Pair("Top Rated", SearchMode.TOP_RATED),
    )

    private val searchSortOptions = arrayOf(
        Pair("Latest", 0),
        Pair("Top Rated", 1),
        Pair("Most Viewed", 2),
    )

    private val windowOptions = arrayOf(
        Pair("Daily", WindowValue(hotDays = 10, postedFromDays = 1)),
        Pair("Weekly", WindowValue(hotDays = 20, postedFromDays = 7)),
        Pair("Monthly", WindowValue(hotDays = 30, postedFromDays = 30)),
        Pair("All time", WindowValue(hotDays = null, postedFromDays = null)),
    )

    open class SelectFilter<T>(
        name: String,
        private val options: Array<Pair<String, T>>,
        defaultState: Int = 0,
    ) : AnimeFilter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
        defaultState,
    ) {
        fun selectedValue(): T = options[state].second
    }

    class ModeFilter : SelectFilter<SearchMode>("Mode", modeOptions)

    class SearchSortFilter : SelectFilter<Int>("Sort (Search only)", searchSortOptions)

    class WindowFilter : SelectFilter<WindowValue>("Window", windowOptions, defaultState = 1)

    class TagsFilter : AnimeFilter.Text("Tags (Search only)", "")

    val FILTER_LIST get() = AnimeFilterList(
        ModeFilter(),
        SearchSortFilter(),
        TagsFilter(),
        WindowFilter(),
    )

    data class SearchParams(
        val mode: SearchMode = SearchMode.TAG_SEARCH,
        val sortBy: Int = 0,
        val includeTags: List<String> = emptyList(),
        val hotDays: Int = 20,
        val postedFromDays: Int? = null,
    )

    fun getSearchParameters(query: String, filters: AnimeFilterList): SearchParams {
        if (filters.isEmpty()) return SearchParams(includeTags = Rule34WorldUtils.parseSearchTags(query))

        val mode = filters.list.filterIsInstance<ModeFilter>()
            .firstOrNull()
            ?.selectedValue()
            ?: SearchMode.TAG_SEARCH

        return when (mode) {
            SearchMode.TAG_SEARCH -> SearchParams(
                mode = SearchMode.TAG_SEARCH,
                sortBy = filters.list.filterIsInstance<SearchSortFilter>()
                    .firstOrNull()
                    ?.selectedValue()
                    ?: 0,
                includeTags = searchTags(query, filters),
            )
            SearchMode.HOT -> SearchParams(
                mode = SearchMode.HOT,
                hotDays = windowValue(filters).hotDays ?: 20,
            )
            SearchMode.TOP_RATED -> SearchParams(
                mode = SearchMode.TOP_RATED,
                sortBy = 1,
                postedFromDays = windowValue(filters).postedFromDays,
            )
        }
    }

    private fun searchTags(query: String, filters: AnimeFilterList): List<String> {
        val filterTags = filters.list.filterIsInstance<TagsFilter>()
            .firstOrNull()
            ?.state
            .orEmpty()

        return Rule34WorldUtils.parseSearchTags(
            listOf(query, filterTags)
                .filter(String::isNotBlank)
                .joinToString(","),
        )
    }

    private fun windowValue(filters: AnimeFilterList): WindowValue = filters.list
        .filterIsInstance<WindowFilter>()
        .firstOrNull()
        ?.selectedValue()
        ?: windowOptions[1].second

    data class WindowValue(
        val hotDays: Int?,
        val postedFromDays: Int?,
    )
}
