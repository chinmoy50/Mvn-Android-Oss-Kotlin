package com.kickstarter.ui.toolbars

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.GravityCompat
import com.kickstarter.R
import com.kickstarter.libs.KSString
import com.kickstarter.libs.featureflag.FlagKey
import com.kickstarter.libs.utils.extensions.getEnvironment
import com.kickstarter.services.DiscoveryParams
import com.kickstarter.ui.activities.DiscoveryActivity
import com.kickstarter.ui.extensions.presentSearchActivity

class DiscoveryToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : KSToolbar(context, attrs, defStyleAttr) {
    private lateinit var ksString: KSString

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (isInEditMode) {
            return
        }
        ksString = requireNotNull(environment().ksString())

        (findViewById<ImageButton>(R.id.menu_button)).setOnClickListener {
            menuButtonClick()
        }

        (findViewById<TextView>(R.id.filter_text_view)).setOnClickListener {
            menuButtonClick()
        }

        (findViewById<ImageButton>(R.id.search_button)).setOnClickListener {
            searchButtonClick()
        }
    }

    protected fun menuButtonClick() {
        val activity = context as DiscoveryActivity
        activity.discoveryLayout().openDrawer(GravityCompat.START)
    }

    fun loadParams(params: DiscoveryParams) {
        val activity = context as DiscoveryActivity
        (findViewById<TextView>(R.id.filter_text_view)).text = params.filterString(activity, ksString, true, false)
    }

    private fun searchButtonClick() {
        val ffEnabled = context.getEnvironment()?.featureFlagClient()?.getBoolean(FlagKey.ANDROID_SEARCH_FILTER) ?: false
        val context = context as? DiscoveryActivity

        context?.presentSearchActivity(featureFlagEnabled = ffEnabled)
    }
}
