package com.kickstarter.utils
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams

data class LayoutPaddingConfig(
    val layout: ViewGroup,
    val applyTopPadding: Boolean = true,
    val applyBottomPadding: Boolean = true
)

object WindowInsetsUtil {

    /**
     * Sets up the activity or fragment for edge-to-edge display and adjusts padding
     * for system bars (status bar, navigation bar).
     *
     * @param window The window of the activity or fragment.
     * @param rootView The root view where the window insets will be applied..
     */
    fun manageEdgeToEdge(
        window: Window,
        rootView: View
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Apply the insets as a margin to the view. This solution sets
            // only the bottom, left, and right dimensions, but you can apply whichever
            // insets are appropriate to your layout. You can also update the view padding
            // if that's more appropriate.
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
                topMargin = insets.top
            }

            // Return CONSUMED if you don't want want the window insets to keep passing
            // down to descendant views.
            WindowInsetsCompat.CONSUMED
        }
    }

    /**
     * Sets up the activity or fragment for edge-to-edge display and adjusts padding for system bars
     * (status bar, navigation bar) for multiple layouts with configurable padding options.
     *
     * @param window The window of the activity or fragment.
     * @param rootView The root view where the window insets will be applied.
     * @param layoutConfigs A list of LayoutPaddingConfig, which holds the layout and its padding configurations.
     */
    fun manageEdgeToEdgeOnMultipleLayouts(
        window: Window,
        rootView: View,
        layoutConfigs: List<LayoutPaddingConfig>
    ) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            layoutConfigs.forEach { config ->
                config.layout.setPadding(
                    config.layout.left,
                    if (config.applyTopPadding) systemBarsInsets.top else config.layout.paddingTop,
                    config.layout.right,
                    if (config.applyBottomPadding) systemBarsInsets.bottom else config.layout.paddingBottom
                )
            }
            WindowInsetsCompat.CONSUMED
        }
    }
}
