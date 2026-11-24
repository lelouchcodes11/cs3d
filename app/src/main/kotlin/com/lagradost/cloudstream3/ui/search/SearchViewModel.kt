package com.lagradost.cloudstream3.ui.search

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKeys
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.debugAssert
import com.lagradost.cloudstream3.mvvm.debugWarning
import com.lagradost.cloudstream3.mvvm.launchSafe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class ExpandableSearchList(
    var list: List<SearchResponse>, var currentPage: Int, var hasNext: Boolean,
)

const val SEARCH_HISTORY_KEY = "search_history"

class SearchViewModel : ViewModel() {
    private val _searchResponse: MutableLiveData<Resource<ExpandableSearchList>> =
        MutableLiveData()
    val searchResponse: LiveData<Resource<ExpandableSearchList>> get() = _searchResponse

    private val _currentSearch: MutableLiveData<Map<String, ExpandableSearchList>> =
        MutableLiveData()
    val currentSearch: LiveData<Map<String, ExpandableSearchList>> get() = _currentSearch

    private val _currentHistory: MutableLiveData<List<SearchHistoryItem>> = MutableLiveData()
    val currentHistory: LiveData<List<SearchHistoryItem>> get() = _currentHistory

    private val _searchSuggestions: MutableLiveData<List<String>> = MutableLiveData()
    val searchSuggestions: LiveData<List<String>> get() = _searchSuggestions

    // desktop: which extensions are still searching (the page shows each extension as soon as it answers)
    data class SearchProgress(val pending: List<String>, val total: Int)

    private val _progress: MutableLiveData<SearchProgress> = MutableLiveData(SearchProgress(emptyList(), 0))
    val progress: LiveData<SearchProgress> get() = _progress

    private var suggestionJob: Job? = null

    private var repos = apis.withLock { apis.map { APIRepository(it) } }

    /** desktop: the error of a host name that does not resolve: throwAbleToResource's text for an UnknownHostException, or the message itself (as the extension sandbox passes it on) */
    internal fun noSuchHost(error: String): Boolean = listOf("Cannot connect to server, try again later.", "No such host is known", "UnknownHostException", "Unable to resolve host", "nodename nor servname")
        .any { error.contains(it, ignoreCase = true) }

    /**
     * desktop: also stops the search that is running. The page clears the results when its query is empty or one letter, and a search still
     * running then filled them in again: the results of the earlier query showed under the new one.
     */
    fun clearSearch() {
        currentSearchIndex++
        onGoingSearch?.cancel()
        clearResults()
    }

    private fun clearResults() {
        _searchResponse.postValue(Resource.Success(ExpandableSearchList(emptyList(), 0, false)))
        _currentSearch.postValue(emptyMap())
        _progress.postValue(SearchProgress(emptyList(), 0))
        synchronized(expandableSearches) { expandableSearches.clear() }
    }

    // a copy, so the page sees a changed value (the live map is edited in place by the searches that are running)
    private fun publishSearches() {
        val copy = synchronized(expandableSearches) { LinkedHashMap(expandableSearches.mapValues { it.value.copy() }) }
        _currentSearch.postValue(copy)
    }

    var lastQuery: String? = null

    /** Save which providers can searched again and which search result page they are on.
     * Maps provider name to search list.
     * @see [HomeViewModel.expandable] */
    private val expandableSearches: MutableMap<String, ExpandableSearchList> = mutableMapOf()

    @Volatile private var currentSearchIndex = 0
    private var onGoingSearch: Job? = null

    fun reloadRepos() {
        repos = apis.withLock { apis.map { APIRepository(it) } }
    }

    // desktop: the native Search page never called reloadRepos (the Android fragment does, on creation and after plugins load), so a
    // search made after this view model was created while extensions were still loading only asked those. Follows the loaded
    // extensions now; rebuilt only when they changed because every APIRepository registers a listener.
    private fun refreshRepos() {
        val current = apis.withLock { apis.toList() }
        if (current.size != repos.size || current.indices.any { current[it] !== repos[it].api }) {
            repos = current.map { APIRepository(it) }
        }
    }

    fun searchAndCancel(
        query: String,
        providersActive: Set<String> = setOf(),
        ignoreSettings: Boolean = false,
        isQuickSearch: Boolean = false,
    ) {
        currentSearchIndex++
        onGoingSearch?.cancel()
        onGoingSearch = search(query, providersActive, ignoreSettings, isQuickSearch)
    }

    fun updateHistory() = ioSafe {
        val items = getKeys("$currentAccount/$SEARCH_HISTORY_KEY")?.mapNotNull {
            getKey<SearchHistoryItem>(it)
        }?.sortedByDescending { it.searchedAt } ?: emptyList()
        _currentHistory.postValue(items)
    }

    /**
     * Fetches search suggestions with debouncing.
     * Waits 300ms before making the API call to avoid too many requests.
     * 
     * @param query The search query to get suggestions for
     */
    fun fetchSuggestions(query: String) {
        suggestionJob?.cancel()
        
        if (query.isBlank() || query.length < 2) {
            _searchSuggestions.postValue(emptyList())
            return
        }
        
        suggestionJob = ioSafe {
            delay(300) // Debounce
            val suggestions = SearchSuggestionApi.getSuggestions(query)
            // desktop: getSuggestions answers a cancelled request too (with nothing, or with what came late): only the newest text is shown
            if (!isActive) return@ioSafe
            _searchSuggestions.postValue(suggestions)
        }
    }

    /**
     * Clears the current search suggestions.
     */
    fun clearSuggestions() {
        suggestionJob?.cancel()
        _searchSuggestions.postValue(emptyList())
    }

    private val lock: MutableSet<String> = java.util.Collections.synchronizedSet(mutableSetOf())

    // ExpandableHomepageList because the home adapter is reused in the search fragment
    // desktop: [forQuery] is the search the caller shows; when another search was made since (Back to a "See all" page of an earlier search),
    // nothing is asked for: the next page of the new query's list was handed to it, and its titles replaced the ones it showed
    suspend fun expandAndReturn(name: String, forQuery: String? = null): HomeViewModel.ExpandableHomepageList? {
        val query = lastQuery ?: return null
        if (forQuery != null && forQuery != query) return null
        val repo = repos.find { it.name == name } ?: return null
        if (!lock.add(name)) return null
        // desktop: the lock is let go however this ends (a "See all" page left while its next page loaded kept the extension locked for good)
        try {
            return expandLocked(name, query, repo)
        } finally {
            lock -= name
        }
    }

    private suspend fun expandLocked(name: String, query: String, repo: APIRepository): HomeViewModel.ExpandableHomepageList? {
        val searchIndex = currentSearchIndex
        expandableSearches[name]?.let { current ->
            debugAssert({ !current.hasNext }) {
                "Expand called when not needed"
            }

            val nextPage = current.currentPage + 1
            val next = repo.search(query, nextPage)
            // desktop: a new search started while this page loaded: its results are in the map now, and this page of the old query is not theirs
            if (currentSearchIndex != searchIndex || expandableSearches[name] !== current) return null
            if (next is Resource.Success) {
                val nextValue = next.value
                expandableSearches[name]?.apply {
                    this.hasNext = nextValue.hasNext
                    this.currentPage = nextPage

                    debugWarning({ nextValue.items.any { outer -> this.list.any { it.url == outer.url } } }) {
                        "Expanded search contained an item that was previously already in the list.\nQuery = $query, ${nextValue.items} = ${this.list}"
                    }

                    // just to be sure we are not adding the same shit for some reason
                    // Avoids weird behavior in the recyclerview by recreating the list
                    this.list = (this.list + nextValue.items).distinctBy { it.url }
                } ?: debugWarning {
                    "Expanded an item not in search load named $name, current list is ${expandableSearches.keys}"
                }
            } else {
                current.hasNext = false
            }

            _searchResponse.postValue(Resource.Success(synchronized(expandableSearches) { bundleSearch(expandableSearches) }))
            publishSearches()
        }

        val item = expandableSearches[name] ?: return null
        return HomeViewModel.ExpandableHomepageList(
            HomePageList(name, item.list),
            item.currentPage,
            item.hasNext
        )
    }

    private fun bundleSearch(lists: MutableMap<String, ExpandableSearchList>): ExpandableSearchList {
        if (lists.size == 1) {
            return lists.values.first()
        }

        val list = ArrayList<SearchResponse>()
        val nestedList =
            lists.map { it.value.list }

        // I do it this way to move the relevant search results to the top
        var index = 0
        while (true) {
            var added = 0
            for (sublist in nestedList) {
                if (sublist.size > index) {
                    list.add(sublist[index])
                    added++
                }
            }
            if (added == 0) break
            index++
        }

        return ExpandableSearchList(list, 1, false)
    }

    private fun search(
        query: String,
        providersActive: Set<String>,
        ignoreSettings: Boolean = false,
        isQuickSearch: Boolean = false,
    ) =
        viewModelScope.launchSafe {
            val currentIndex = currentSearchIndex
            if (query.length <= 1) {
                clearResults()
                return@launchSafe
            }

            if (!isQuickSearch) {
                val key = query.hashCode().toString()
                setKey(
                    "$currentAccount/$SEARCH_HISTORY_KEY",
                    key,
                    SearchHistoryItem(
                        searchedAt = System.currentTimeMillis(),
                        searchText = query,
                        type = emptyList(), // TODO implement tv type
                        key = key,
                    )
                )
            }

            refreshRepos()
            val chosen = repos.filter { a ->
                (ignoreSettings || (providersActive.isEmpty() || providersActive.contains(a.name))) && (!isQuickSearch || a.hasQuickSearch) &&
                    com.lagradost.desktop.core.Kids.allows(a.api)
            }
            // desktop: every extension that answers is published at once, the ones still searching stay listed as pending
            val pending = LinkedHashSet(chosen.map { it.name })
            synchronized(expandableSearches) { expandableSearches.clear() }
            _searchResponse.postValue(Resource.Loading())
            _currentSearch.postValue(emptyMap())
            _progress.postValue(SearchProgress(pending.toList(), chosen.size))

            lastQuery = query

            withContext(Dispatchers.IO) { // This interrupts UI otherwise
                chosen.amap { a -> // Parallel
                    var search = if (isQuickSearch) a.quickSearch(query) else a.search(query, 1)
                    // desktop: a provider that failed (timeout, dropped connection, busy host) is asked again, up to twice;
                    // flaky hosts usually answer the second time, otherwise the user sees results from "some sources only"
                    // A host name that does not exist (a dead IPTV playlist, a site that moved) is not flaky: asked again it fails the same way and the
                    // source stayed "still searching" ~3 s longer on every search. The error text is a message and stack lines: only its first line is logged.
                    var attempt = 0
                    while (search is Resource.Failure && attempt < 2 && currentSearchIndex == currentIndex && !noSuchHost(search.errorString)) {
                        android.util.Log.w("SearchVM", "${a.name}: ${search.errorString.lineSequence().firstOrNull { it.isNotBlank() }?.take(160)}, retrying")
                        attempt++
                        kotlinx.coroutines.delay(700L * attempt)
                        search = if (isQuickSearch) a.quickSearch(query) else a.search(query, 1)
                    }
                    if (search is Resource.Failure) android.util.Log.w("SearchVM", "${a.name} failed: ${search.errorString.lineSequence().firstOrNull { it.isNotBlank() }?.take(200)}")
                    // desktop: the check is made under the lock the next search clears the results with; made before it, an answer that came
                    // just as a new search started was put among the new query's results
                    val stale = synchronized(expandableSearches) {
                        if (currentSearchIndex != currentIndex) return@synchronized true
                        if (search is Resource.Success && search.value.items.isNotEmpty()) {
                            val searchValue = search.value
                            expandableSearches[a.name] =
                                ExpandableSearchList(searchValue.items, 1, searchValue.hasNext)
                        }
                        pending.remove(a.name)
                        false
                    }
                    if (stale) return@amap
                    publishSearches()
                    val left = synchronized(expandableSearches) { pending.toList() }
                    if (currentSearchIndex == currentIndex) _progress.postValue(SearchProgress(left, chosen.size))
                }

                if (currentSearchIndex != currentIndex) return@withContext // this should prevent rewrite of existing data bug

                publishSearches()
                val list = synchronized(expandableSearches) { bundleSearch(expandableSearches) }

                _searchResponse.postValue(Resource.Success(list))
            }
        }
}
