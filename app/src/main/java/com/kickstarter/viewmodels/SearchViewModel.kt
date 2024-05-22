package com.kickstarter.viewmodels

import android.content.Intent
import android.util.Pair
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import com.apollographql.apollo.api.CustomTypeValue
import com.kickstarter.libs.Environment
import com.kickstarter.libs.RefTag
import com.kickstarter.libs.featureflag.FlagKey
import com.kickstarter.libs.graphql.DateTimeAdapter
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.utils.ListUtils
import com.kickstarter.libs.utils.extensions.addToDisposable
import com.kickstarter.libs.utils.extensions.isNotNull
import com.kickstarter.libs.utils.extensions.isPresent
import com.kickstarter.libs.utils.extensions.isTrimmedEmpty
import com.kickstarter.models.Project
import com.kickstarter.services.ApiClientTypeV2
import com.kickstarter.services.DiscoveryParams
import com.kickstarter.services.apiresponses.DiscoverEnvelope
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.rx2.asFlow
import java.util.concurrent.TimeUnit

class SearchPagingSource(
    private val apiClient: ApiClientTypeV2,
    private val queryParams: DiscoveryParams
) : PagingSource<Pair<DiscoveryParams?, String?>, Project>() {
    override fun getRefreshKey(state: PagingState<Pair<DiscoveryParams?, String?>, Project>): Pair<DiscoveryParams?, String?> {
        return Pair(queryParams, null)
    }

    override suspend fun load(params: LoadParams<Pair<DiscoveryParams?, String?>>): LoadResult<Pair<DiscoveryParams?, String?>, Project> {
        return try {
            var projectsList = emptyList<Project>()

            val discoveryParams = params.key?.first

            val currentPageUrl = params.key?.second
            var nextPageUrl: String? = null

            if (currentPageUrl.isNotNull()) {
                // - Following pages will call the endpoint, with a paging URL.
                // In an ideal implementation calling the network layer should be suspend functions, not converted to a flow Stream
                apiClient.fetchProjects(requireNotNull(currentPageUrl)).asFlow().collect {
                    projectsList = it.projects()
                    nextPageUrl = it.urls()?.api()?.moreProjects()
                }
            } else {
                // - First page requires discovery query params either the search one or the default ones
                // In an ideal implementation calling the network layer should be suspend functions, not converted to a flow Stream
                apiClient.fetchProjects(queryParams).asFlow().collect {
                    projectsList = it.projects()
                    nextPageUrl = it.urls()?.api()?.moreProjects()
                }
            }

            return LoadResult.Page(
                data = projectsList,
                prevKey = null, // only forward pagination
                nextKey = Pair(discoveryParams, nextPageUrl)
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
}

interface SearchViewModel {
    interface Inputs {
        /** Call when the next page has been invoked.  */
        fun nextPage()

        /** Call when a project is tapped in search results.  */
        fun projectClicked(project: Project)

        /** Call when text changes in search box.  */
        fun search(s: String)
    }

    interface Outputs {
        /** Emits a boolean indicating whether projects are being fetched from the API.  */
        fun isFetchingProjects(): Observable<Boolean>

        /** Emits list of popular projects.  */
        fun popularProjects(): Observable<List<Project>>

        /** Emits list of projects matching criteria.  */
        fun searchProjects(): Observable<List<Project>>

        /** Emits a project and ref tag when we should start a project activity.  */
        fun startProjectActivity(): Observable<Pair<Project, RefTag>>

        /** Emits a Project and RefTag pair when we should start the [com.kickstarter.ui.activities.PreLaunchProjectPageActivity].  */
        fun startPreLaunchProjectActivity(): Observable<Pair<Project, RefTag>>
    }

    class SearchViewModel(
        private val environment: Environment,
        private val intent: Intent? = null
    ) : ViewModel(), Inputs, Outputs {

        private val apiClient = requireNotNull(environment.apiClientV2())
        private val discoverEnvelope = PublishSubject.create<DiscoverEnvelope>()
        private val sharedPreferences = requireNotNull(environment.sharedPreferences())
        private val cookieManager = requireNotNull(environment.cookieManager())
        private val scheduler = environment.schedulerV2()
        private val analyticEvents = requireNotNull(environment.analytics())

        /**
         * Returns a project and its appropriate ref tag given its location in a list of popular projects or search results.
         *
         * @param searchTerm        The search term entered to determine list of search results.
         * @param projects          The list of popular or search result projects.
         * @param selectedProject   The project selected by the user.
         * @return The project and its appropriate ref tag.
         */
        private fun projectAndRefTag(
            searchTerm: String,
            projects: List<Project>,
            selectedProject: Project
        ): Pair<Project, RefTag> {
            val isFirstResult = if (projects.isEmpty()) false else selectedProject === projects[0]
            return if (searchTerm.isEmpty()) {
                if (isFirstResult) Pair.create(
                    selectedProject,
                    RefTag.searchPopularFeatured()
                ) else Pair.create(selectedProject, RefTag.searchPopular())
            } else {
                if (isFirstResult) Pair.create(
                    selectedProject,
                    RefTag.searchFeatured()
                ) else Pair.create(selectedProject, RefTag.search())
            }
        }

        private val nextPage = PublishSubject.create<Unit>()
        private val projectClicked = PublishSubject.create<Project>()
        private val search = PublishSubject.create<String>()
        private val isFetchingProjects = BehaviorSubject.create<Boolean>()
        private val popularProjects = BehaviorSubject.create<List<Project>>()
        private val searchProjects = BehaviorSubject.create<List<Project>>()
        private val startProjectActivity = PublishSubject.create<Pair<Project, RefTag>>()
        private val startPreLaunchProjectActivity = PublishSubject.create<Pair<Project, RefTag>>()
        private val ffClient = requireNotNull(environment.featureFlagClient())
        private val disposables = CompositeDisposable()

        @JvmField
        val inputs: Inputs = this

        @JvmField
        val outputs: Outputs = this

        fun clearSearchedProjects() {
            searchProjects.onNext(listOf())
        }

        override fun nextPage() {
            nextPage.onNext(Unit)
        }

        override fun projectClicked(project: Project) {
            projectClicked.onNext(project)
        }

        override fun search(s: String) {
            search.onNext(s)
        }

        override fun startProjectActivity(): Observable<Pair<Project, RefTag>> {
            return startProjectActivity
        }

        override fun startPreLaunchProjectActivity(): Observable<Pair<Project, RefTag>> {
            return startPreLaunchProjectActivity
        }

        override fun isFetchingProjects(): Observable<Boolean> {
            return isFetchingProjects
        }

        fun setIsFetching(fetching: Boolean) {
            isFetchingProjects.onNext(fetching)
        }

        override fun popularProjects(): Observable<List<Project>> {
            return popularProjects
        }

        override fun searchProjects(): Observable<List<Project>> {
            return searchProjects
        }

        companion object {
            private val defaultSort = DiscoveryParams.Sort.POPULAR
            private val defaultParams = DiscoveryParams.builder().sort(defaultSort).build()
        }

        private val _uiState = MutableStateFlow<PagingData<Project>>(PagingData.empty())
        val projectListUiState: StateFlow<PagingData<Project>> = _uiState.asStateFlow()

//        val projectList = Pager(
//            PagingConfig(
//                pageSize = 15,
//                prefetchDistance = 3,
//                enablePlaceholders = true
//            )
//        ) {
//            SearchPagingSource(apiClient, defaultParams)
//        }.flow.cachedIn(viewModelScope)

        fun onSearchClicked(params: DiscoveryParams = defaultParams) {
            viewModelScope.launch {
                try {
                    Pager(
                        PagingConfig(
                            pageSize = 15,
                            prefetchDistance = 3,
                            enablePlaceholders = true
                        )
                    ) {
                        SearchPagingSource(apiClient, params)
                    }
                        .flow
                        .cachedIn(viewModelScope)
                        .collectLatest { pagingData ->
                            _uiState.value = pagingData
                        }
                } catch (e: Exception) {
                }
            }
        }
        init {
            viewModelScope.launch {

                val searchParams = search
                    .filter { it.isNotNull() }
                    .filter { it.isPresent() }
                    .debounce(300, TimeUnit.MILLISECONDS, scheduler)
                    .map { DiscoveryParams.builder().term(it).build() }

                val popularParams = search
                    .filter { it.isNotNull() }
                    .filter { it.isTrimmedEmpty() }
                    .map { defaultParams }
                    .startWith(defaultParams)

                Observable.merge(searchParams, popularParams) // Convert to UIState and coroutines
                    .subscribe {
                        onSearchClicked(it)
                    }.addToDisposable(disposables)

//                val paginator = ApiPaginatorV2.builder<Project, DiscoverEnvelope, DiscoveryParams>()
//                    .nextPage(nextPage)
//                    .startOverWith(params)
//                    .envelopeToListOfData { envelope: DiscoverEnvelope ->
//                        discoverEnvelope.onNext(envelope)
//                        envelope.projects()
//                    }
//                    .envelopeToMoreUrl { env: DiscoverEnvelope ->
//                        env.urls()?.api()?.moreProjects()
//                    }
//                    .clearWhenStartingOver(true)
//                    .concater { xs: List<Project>, ys: List<Project> ->
//                        ListUtils.concatDistinct(
//                            xs,
//                            ys
//                        )
//                    }
//                    .loadWithParams {
//                        apiClient.fetchProjects(it)
//                    }
//                    .loadWithPaginationPath {
//                        apiClient.fetchProjects(it)
//                    }
//                    .build()

//                paginator.isFetching
//                    .subscribe(isFetchingProjects)

                search
                    .filter { it.isNotNull() }
                    .filter { it.isTrimmedEmpty() }
                    .subscribe { searchProjects.onNext(ListUtils.empty()) }
                    .addToDisposable(disposables)
//
//                params
//                    .compose(Transformers.takePairWhenV2(paginator.paginatedData()))
//                    .subscribe { paramsAndProjects: Pair<DiscoveryParams, List<Project>> ->
//                        if (paramsAndProjects.first.sort() == defaultSort) {
//                            popularProjects.onNext(paramsAndProjects.second)
//                        } else {
//                            searchProjects.onNext(paramsAndProjects.second)
//                        }
//                    }
//                    .addToDisposable(disposables)

                // val pageCount = paginator.loadingPage()
                val projects = Observable.merge(popularProjects, searchProjects)

//                params.compose(Transformers.takePairWhenV2(projectClicked))
//                    .compose(Transformers.combineLatestPair(pageCount))
//                    .subscribe { projectDiscoveryParamsPair: Pair<Pair<DiscoveryParams, Project>, Int> ->
//                        val refTag = RefTagUtils.projectAndRefTagFromParamsAndProject(
//                            projectDiscoveryParamsPair.first.first,
//                            projectDiscoveryParamsPair.first.second
//                        )
//                        val cookieRefTag = RefTagUtils.storedCookieRefTagForProject(
//                            projectDiscoveryParamsPair.first.second,
//                            cookieManager,
//                            sharedPreferences
//                        )
//                        val projectData = builder()
//                            .refTagFromIntent(refTag.second)
//                            .refTagFromCookie(cookieRefTag)
//                            .project(projectDiscoveryParamsPair.first.second)
//                            .build()
//
//                        analyticEvents.trackDiscoverSearchResultProjectCATClicked(
//                            projectDiscoveryParamsPair.first.first,
//                            projectData,
//                            projectDiscoveryParamsPair.second,
//                            defaultSort
//                        )
//                    }
//                    .addToDisposable(disposables)

                val selectedProject =
                    Observable.combineLatest<String, List<Project>, Pair<String, List<Project>>>(
                        search,
                        projects
                    ) { a: String, b: List<Project> ->
                        Pair.create(a, b)
                    }
                        .compose(Transformers.takePairWhenV2(projectClicked))
                        .map { searchTermAndProjectsAndProjectClicked: Pair<Pair<String, List<Project>>, Project> ->
                            val searchTerm = searchTermAndProjectsAndProjectClicked.first.first
                            val currentProjects =
                                searchTermAndProjectsAndProjectClicked.first.second
                            val projectClicked = searchTermAndProjectsAndProjectClicked.second
                            projectAndRefTag(searchTerm, currentProjects, projectClicked)
                        }

                selectedProject.subscribe {
                    if (it.first.launchedAt() == DateTimeAdapter().decode(
                            CustomTypeValue.fromRawValue(
                                    0
                                )
                        ) &&
                        ffClient.getBoolean(FlagKey.ANDROID_PRE_LAUNCH_SCREEN)
                    ) {
                        startPreLaunchProjectActivity.onNext(it)
                    } else {
                        startProjectActivity.onNext(it)
                    }
                }
                    .addToDisposable(disposables)

//                params
//                    .compose(Transformers.takePairWhenV2(discoverEnvelope))
//                    .compose(Transformers.combineLatestPair(pageCount))
//                    .filter { it: Pair<Pair<DiscoveryParams, DiscoverEnvelope>, Int> ->
//                        (
//                                it.first.first.term().isNotNull() &&
//                                        it.first.first.term()?.isPresent() ?: false &&
//                                        it.first.first.sort() != defaultSort &&
//                                        it.second.intValueOrZero() == 1
//                                )
//                    }
//                    .distinct()
//                    .subscribe { it: Pair<Pair<DiscoveryParams, DiscoverEnvelope>, Int> ->
//                        analyticEvents.trackSearchResultPageViewed(
//                            it.first.first,
//                            it.first.second.stats()?.count() ?: 0,
//                            defaultSort
//                        )
//                    }
//                    .addToDisposable(disposables)

                analyticEvents.trackSearchCTAButtonClicked(defaultParams)
            }
        }

        override fun onCleared() {
            disposables.clear()
            super.onCleared()
        }
    }

    class Factory(private val environment: Environment, private val intent: Intent? = null) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SearchViewModel(environment, intent) as T
        }
    }
}
