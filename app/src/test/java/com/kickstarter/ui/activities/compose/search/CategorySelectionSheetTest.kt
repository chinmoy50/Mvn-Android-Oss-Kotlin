package com.kickstarter.ui.activities.compose.search

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.kickstarter.KSRobolectricTestCase
import com.kickstarter.ui.compose.designsystem.KSTheme
import org.junit.Test

class CategorySelectionSheetTest : KSRobolectricTestCase() {

    private val dismissButton =
        composeTestRule.onNodeWithTag(CategorySelectionSheetTestTag.DISMISS_BUTTON.name)

    @Test
    fun `test tapping dismiss button should register dismiss`() {
        var applyClickCount = 0
        var dismissClickCount = 0

        composeTestRule.setContent {
            KSTheme {
                CategorySelectionSheet(
                    categories = listOf(),
                    onDismiss = { dismissClickCount++ },
                    onApply = { applyClickCount++ },
                    isLoading = false
                )
            }
        }

        dismissButton.performClick()
        assertEquals(0, applyClickCount)
        assertEquals(1, dismissClickCount)
    }
}
