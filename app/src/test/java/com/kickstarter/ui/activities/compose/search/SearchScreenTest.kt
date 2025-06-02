package com.kickstarter.ui.activities.compose.search

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetValue.Hidden
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.platform.app.InstrumentationRegistry
import com.kickstarter.KSRobolectricTestCase
import com.kickstarter.R
import com.kickstarter.mock.factories.CategoryFactory
import com.kickstarter.models.Category
import com.kickstarter.models.Project
import com.kickstarter.services.DiscoveryParams
import com.kickstarter.ui.compose.designsystem.KSTheme
import org.junit.Test

class SearchScreenTest : KSRobolectricTestCase() {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private val backButton = composeTestRule.onNodeWithTag(SearchScreenTestTag.BACK_BUTTON.name)
    private val searchTextInput =
        composeTestRule.onNodeWithTag(SearchScreenTestTag.SEARCH_TEXT_INPUT.name)
    private val emptyView = composeTestRule.onNodeWithTag(SearchScreenTestTag.EMPTY_VIEW.name)
    private val loadingView = composeTestRule.onNodeWithTag(SearchScreenTestTag.LOADING_VIEW.name)
    private val inListLoadingView =
        composeTestRule.onNodeWithTag(SearchScreenTestTag.IN_LIST_LOADING_VIEW.name)
    private val listView = composeTestRule.onNodeWithTag(SearchScreenTestTag.LIST_VIEW.name)
    private val popularProjectsTitle =
        composeTestRule.onNodeWithTag(SearchScreenTestTag.DISCOVER_PROJECTS_TITLE.name)
    private val featuredProjectView =
        composeTestRule.onNodeWithTag(SearchScreenTestTag.FEATURED_PROJECT_VIEW.name)

    @Test
    fun testEmptyComponentsVisible() {
        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = false,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = true,
                    categories = listOf(),
                    onSearchTermChanged = {},
                    onItemClicked = {}
                )
            }
        }

        backButton.assertIsDisplayed()
        searchTextInput.assertIsDisplayed()
        emptyView.assertIsDisplayed()
        loadingView.assertDoesNotExist()
        inListLoadingView.assertDoesNotExist()
        listView.assertDoesNotExist()
        popularProjectsTitle.assertDoesNotExist()
        featuredProjectView.assertDoesNotExist()
    }

    @Test
    fun testPopularListComponentsVisible() {
        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = false,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = false,
                    isDefaultList = true,
                    itemsList = List(20) {
                        Project.builder()
                            .name("This is a test $it")
                            .pledged((it * 2).toDouble())
                            .goal(20.0)
                            .state(if (it in 10..20) Project.STATE_SUBMITTED else Project.STATE_LIVE)
                            .build()
                    },
                    categories = listOf(),
                    onSearchTermChanged = {},
                    onItemClicked = {}
                )
            }
        }

        backButton.assertIsDisplayed()
        searchTextInput.assertIsDisplayed()
        emptyView.assertDoesNotExist()
        loadingView.assertDoesNotExist()
        inListLoadingView.assertDoesNotExist()
        listView.assertIsDisplayed()

        val popularProjectTitleText = context.getString(R.string.activity_empty_state_logged_in_button)
        popularProjectsTitle.assertIsDisplayed()
        popularProjectsTitle.assertTextEquals(popularProjectTitleText)

        featuredProjectView.assertIsDisplayed()

        for (i in 1..19) {
            listView.performScrollToIndex(i)
            val projectView =
                composeTestRule.onNodeWithTag(SearchScreenTestTag.NORMAL_PROJECT_VIEW.name + i)
            projectView.assertIsDisplayed()
        }
    }

    @Test
    fun testSearchedListComponentsVisible() {
        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = false,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = false,
                    isDefaultList = false,
                    itemsList = List(20) {
                        Project.builder()
                            .name("This is a test $it")
                            .pledged((it * 2).toDouble())
                            .goal(20.0)
                            .state(if (it in 10..20) Project.STATE_SUBMITTED else Project.STATE_LIVE)
                            .build()
                    },
                    categories = listOf(),
                    onSearchTermChanged = {},
                    onItemClicked = {}
                )
            }
        }

        backButton.assertIsDisplayed()
        searchTextInput.assertIsDisplayed()
        emptyView.assertDoesNotExist()
        loadingView.assertDoesNotExist()
        inListLoadingView.assertDoesNotExist()
        listView.assertIsDisplayed()
        popularProjectsTitle.assertDoesNotExist()

        featuredProjectView.assertIsDisplayed()

        for (i in 1..19) {
            listView.performScrollToIndex(i)
            val projectView =
                composeTestRule.onNodeWithTag(SearchScreenTestTag.NORMAL_PROJECT_VIEW.name + i)
            projectView.assertIsDisplayed()
        }
    }

    @Test
    fun testLoadingComponentsEmptyListVisible() {
        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = true,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = false,
                    categories = listOf(),
                    onSearchTermChanged = {},
                    onItemClicked = {}
                )
            }
        }

        backButton.assertIsDisplayed()
        searchTextInput.assertIsDisplayed()
        emptyView.assertDoesNotExist()
        loadingView.assertIsDisplayed()
        inListLoadingView.assertDoesNotExist()
        listView.assertExists()
        popularProjectsTitle.assertDoesNotExist()
        featuredProjectView.assertDoesNotExist()
    }

    @Test
    fun testLoadingComponentsWithListVisible() {
        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = true,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = false,
                    isDefaultList = false,
                    itemsList = List(20) {
                        Project.builder()
                            .name("This is a test $it")
                            .pledged((it * 2).toDouble())
                            .goal(20.0)
                            .state(if (it in 10..20) Project.STATE_SUBMITTED else Project.STATE_LIVE)
                            .build()
                    },
                    categories = listOf(),
                    onSearchTermChanged = {},
                    onItemClicked = {}
                )
            }
        }

        backButton.assertIsDisplayed()
        searchTextInput.assertIsDisplayed()
        emptyView.assertDoesNotExist()
        loadingView.assertDoesNotExist()
        listView.assertIsDisplayed()
        popularProjectsTitle.assertDoesNotExist()

        featuredProjectView.assertIsDisplayed()

        for (i in 1..19) {
            listView.performScrollToIndex(i)
            val projectView =
                composeTestRule.onNodeWithTag(SearchScreenTestTag.NORMAL_PROJECT_VIEW.name + i)
            projectView.assertIsDisplayed()
        }

        inListLoadingView.assertIsDisplayed()
    }

    @Test
    fun testClickActions() {
        var backClickedCount = 0
        var itemClickedCount = 0

        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { backClickedCount++ },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = false,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = false,
                    isDefaultList = false,
                    itemsList = List(20) {
                        Project.builder()
                            .name("This is a test $it")
                            .pledged((it * 2).toDouble())
                            .goal(20.0)
                            .state(if (it in 10..20) Project.STATE_SUBMITTED else Project.STATE_LIVE)
                            .build()
                    },
                    categories = listOf(),
                    onSearchTermChanged = {},
                    onItemClicked = { itemClickedCount++ }
                )
            }
        }

        backButton.performClick()
        assertEquals(backClickedCount, 1)

        featuredProjectView.performClick()
        assertEquals(itemClickedCount, 1)

        for (i in 1..19) {
            listView.performScrollToIndex(i)
            val projectView =
                composeTestRule.onNodeWithTag(SearchScreenTestTag.NORMAL_PROJECT_VIEW.name + i)
            projectView.performClick()
        }

        assertEquals(itemClickedCount, 20)
    }

    @Test
    fun testSearchTermUpdates() {
        var currentSearchTerm = ""

        composeTestRule.setContent {
            KSTheme {
                SearchScreen(
                    onBackClicked = { },
                    scaffoldState = rememberScaffoldState(),
                    isLoading = false,
                    lazyColumnListState = rememberLazyListState(),
                    showEmptyView = false,
                    isDefaultList = false,
                    itemsList = List(20) {
                        Project.builder()
                            .name("This is a test $it")
                            .pledged((it * 2).toDouble())
                            .goal(20.0)
                            .state(if (it in 10..20) Project.STATE_SUBMITTED else Project.STATE_LIVE)
                            .build()
                    },
                    categories = listOf(),
                    onSearchTermChanged = {
                        currentSearchTerm = it
                    },
                    onItemClicked = { }
                )
            }
        }

        searchTextInput.performTextInput("this is a test")
        assertEquals(currentSearchTerm, "this is a test")
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `pager initial State, navigates to category row, then navigate back to filter menu page`() {

        var page = 0
        composeTestRule.setContent {
            val testPagerState = rememberPagerState(initialPage = FilterPages.MAIN_FILTER.ordinal, pageCount = { FilterPages.values().size })
            val testSheetState = rememberModalBottomSheetState(
                initialValue = Hidden,
                skipHalfExpanded = true
            )

            val categories = CategoryFactory.rootCategories()
            val selectedStatus = DiscoveryParams.State.LIVE

            val appliedFilters = mutableListOf<Pair<DiscoveryParams.State?, Category?>>()
            val dismissed = mutableListOf<Boolean>()
            val selectedCounts = mutableListOf<Pair<Int?, Int?>>()

            KSTheme {

                FilterPagerSheet(
                    selectedProjectStatus = selectedStatus,
                    currentCategory = categories[0],
                    categories = categories,
                    onDismiss = { dismissed.add(true) },
                    onApply = { state, category, _ -> appliedFilters.add(Pair(state, category)) },
                    updateSelectedCounts = { statusCount, categoryCount, _ ->
                        selectedCounts.add(
                            statusCount to categoryCount
                        )
                    },
                    pagerState = testPagerState,
                    sheetState = testSheetState,
                    shouldShowPhase = true
                )
            }

            LaunchedEffect(testPagerState.currentPage) { // Update page counter outside compose context
                page = testPagerState.currentPage
            }
        }

        composeTestRule.onNodeWithTag("Category").assertExists() // On Filters page, category row button
        assertEquals(page, FilterPages.MAIN_FILTER.ordinal) // First page is main menu

        composeTestRule.onNodeWithTag("Category").performClick()

        composeTestRule.waitForIdle()
        assertEquals(page, FilterPages.CATEGORIES.ordinal)

        composeTestRule.onNodeWithTag(SearchScreenTestTag.BACK_BUTTON.name).assertExists() // On Category Selection, top left Arrow Icon
        composeTestRule.onNodeWithTag(SearchScreenTestTag.BACK_BUTTON.name).performClick()

        composeTestRule.waitForIdle()
        assertEquals(page, FilterPages.MAIN_FILTER.ordinal)
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `Reset button behaviour on Main filter screen`() {

        val categories = CategoryFactory.rootCategories()

        val appliedFilters = mutableListOf<Any?>()
        val dismissed = mutableListOf<Boolean>()
        val selectedCounts = mutableListOf<Int?>()

        composeTestRule.setContent {
            val testPagerState = rememberPagerState(initialPage = FilterPages.MAIN_FILTER.ordinal, pageCount = { FilterPages.values().size })
            val testSheetState = rememberModalBottomSheetState(
                initialValue = Hidden,
                skipHalfExpanded = true
            )

            KSTheme {

                FilterPagerSheet(
                    selectedProjectStatus = DiscoveryParams.State.LIVE,
                    currentCategory = categories[0],
                    categories = categories,
                    currentPercentage = DiscoveryParams.RaisedBuckets.BUCKET_2,
                    onDismiss = { dismissed.add(true) },
                    onApply = { state, category, bucket ->
                        appliedFilters.add(state)
                        appliedFilters.add(category)
                        appliedFilters.add(bucket)
                    },
                    updateSelectedCounts = { statusCount, categoryCount, bucket ->
                        selectedCounts.add(statusCount)
                        selectedCounts.add(categoryCount)
                        selectedCounts.add(bucket)
                    },
                    pagerState = testPagerState,
                    sheetState = testSheetState,
                    shouldShowPhase = true
                )
            }
        }

        // - Reset button behaviour
        composeTestRule.onNodeWithText(context.resources.getString(R.string.Reset_all_filters))
            .assertExists()

        composeTestRule.onNodeWithText(context.resources.getString(R.string.Reset_filters))
            .assertDoesNotExist()

        composeTestRule.onNodeWithText(context.resources.getString(R.string.Reset_all_filters))
            .performClick()

        assertEquals(appliedFilters.filterNotNull().size, 0)
        assertEquals(selectedCounts.last(), 0)
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `Pager with phase4 ffOff, does not display percentage raised row`() {

        val categories = CategoryFactory.rootCategories()
        val selectedStatus = DiscoveryParams.State.LIVE

        composeTestRule.setContent {
            val testPagerState = rememberPagerState(initialPage = FilterPages.MAIN_FILTER.ordinal, pageCount = { FilterPages.values().size })
            val testSheetState = rememberModalBottomSheetState(
                initialValue = Hidden,
                skipHalfExpanded = true
            )

            KSTheme {

                FilterPagerSheet(
                    selectedProjectStatus = selectedStatus,
                    currentCategory = categories[0],
                    categories = categories,
                    onDismiss = { },
                    onApply = { _, _, _ -> },
                    updateSelectedCounts = { _, _, _ ->
                    },
                    pagerState = testPagerState,
                    sheetState = testSheetState,
                    shouldShowPhase = false
                )
            }
        }

        composeTestRule.onNodeWithTag(FilterMenuTestTags.PERCENTAGE_RAISED_ROW).assertDoesNotExist()
    }

    @OptIn(ExperimentalMaterialApi::class)
    @Test
    fun `Pager with phase4 ffOn, does display percentage raised row, can navigate to PercentageRaised screen then navigate back`() {

        var page = 0
        val categories = CategoryFactory.rootCategories()
        val selectedStatus = DiscoveryParams.State.LIVE

        composeTestRule.setContent {
            val testPagerState = rememberPagerState(initialPage = FilterPages.MAIN_FILTER.ordinal, pageCount = { FilterPages.values().size })
            val testSheetState = rememberModalBottomSheetState(
                initialValue = Hidden,
                skipHalfExpanded = true
            )

            KSTheme {

                FilterPagerSheet(
                    selectedProjectStatus = selectedStatus,
                    currentCategory = categories[0],
                    categories = categories,
                    onDismiss = { },
                    onApply = { _, _, _ -> },
                    updateSelectedCounts = { _, _, _ ->
                    },
                    pagerState = testPagerState,
                    sheetState = testSheetState,
                    shouldShowPhase = true
                )
            }

            LaunchedEffect(testPagerState.currentPage) { // Update page counter outside compose context
                page = testPagerState.currentPage
            }
        }
        assertEquals(page, FilterPages.MAIN_FILTER.ordinal)

        composeTestRule
            .onNodeWithTag(FilterMenuTestTags.LIST)
            .performScrollToNode(hasTestTag(FilterMenuTestTags.PERCENTAGE_RAISED_ROW))

        composeTestRule.onNodeWithTag(FilterMenuTestTags.PERCENTAGE_RAISED_ROW).assertIsDisplayed()
        composeTestRule.onNodeWithTag(FilterMenuTestTags.PERCENTAGE_RAISED_ROW).performClick()

        composeTestRule.waitForIdle()
        assertEquals(page, FilterPages.PERCENTAGE_RAISED.ordinal)

        composeTestRule.onNodeWithTag(SearchScreenTestTag.BACK_BUTTON.name).assertIsDisplayed()
        composeTestRule.onNodeWithTag(SearchScreenTestTag.BACK_BUTTON.name).performClick()

        composeTestRule.waitForIdle()
        assertEquals(page, FilterPages.MAIN_FILTER.ordinal)
    }
}
