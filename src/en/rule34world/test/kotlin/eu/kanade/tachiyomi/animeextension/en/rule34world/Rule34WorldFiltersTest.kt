package eu.kanade.tachiyomi.animeextension.en.rule34world

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import org.junit.Assert.assertEquals
import org.junit.Test

class Rule34WorldFiltersTest {

    @Test
    fun `filter list exposes only mode search sort tags and window`() {
        val filterList = Rule34WorldFilters.FILTER_LIST

        assertEquals(4, filterList.list.size)
        assertEquals(
            listOf(
                Rule34WorldFilters.ModeFilter::class,
                Rule34WorldFilters.SearchSortFilter::class,
                Rule34WorldFilters.TagsFilter::class,
                Rule34WorldFilters.WindowFilter::class,
            ),
            filterList.list.map { it::class },
        )
    }

    @Test
    fun `search sort label says it only applies to search`() {
        val filter = Rule34WorldFilters.FILTER_LIST.list
            .filterIsInstance<Rule34WorldFilters.SearchSortFilter>()
            .first()

        assertEquals("Sort (Search only)", filter.name)
    }

    @Test
    fun `default filters keep tag search mode with query tags`() {
        val params = Rule34WorldFilters.getSearchParameters("animated, sound", AnimeFilterList())

        assertEquals(Rule34WorldFilters.SearchMode.TAG_SEARCH, params.mode)
        assertEquals(0, params.sortBy)
        assertEquals(listOf("animated", "sound"), params.includeTags)
    }

    @Test
    fun `hot mode defaults to weekly site window`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 1
        }

        val params = Rule34WorldFilters.getSearchParameters("ignored", filters)

        assertEquals(Rule34WorldFilters.SearchMode.HOT, params.mode)
        assertEquals(20, params.hotDays)
        assertEquals(emptyList<String>(), params.includeTags)
    }

    @Test
    fun `hot all time falls back to weekly because site has no hot all time`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 1
            list.filterIsInstance<Rule34WorldFilters.WindowFilter>().first().state = 3
        }

        val params = Rule34WorldFilters.getSearchParameters("ignored", filters)

        assertEquals(Rule34WorldFilters.SearchMode.HOT, params.mode)
        assertEquals(20, params.hotDays)
        assertEquals(null, params.postedFromDays)
    }

    @Test
    fun `top rated mode defaults to weekly`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 2
        }

        val params = Rule34WorldFilters.getSearchParameters("ignored", filters)

        assertEquals(Rule34WorldFilters.SearchMode.TOP_RATED, params.mode)
        assertEquals(1, params.sortBy)
        assertEquals(7, params.postedFromDays)
        assertEquals(emptyList<String>(), params.includeTags)
    }

    @Test
    fun `top rated all time omits posted from days`() {
        val filters = Rule34WorldFilters.FILTER_LIST.apply {
            list.filterIsInstance<Rule34WorldFilters.ModeFilter>().first().state = 2
            list.filterIsInstance<Rule34WorldFilters.WindowFilter>().first().state = 3
        }

        val params = Rule34WorldFilters.getSearchParameters("", filters)

        assertEquals(Rule34WorldFilters.SearchMode.TOP_RATED, params.mode)
        assertEquals(1, params.sortBy)
        assertEquals(null, params.postedFromDays)
    }
}
