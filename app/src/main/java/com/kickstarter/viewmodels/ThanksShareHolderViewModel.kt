package com.kickstarter.viewmodels

import android.util.Pair
import com.kickstarter.libs.Environment
import com.kickstarter.libs.RefTag.Companion.thanksFacebookShare
import com.kickstarter.libs.RefTag.Companion.thanksShare
import com.kickstarter.libs.RefTag.Companion.thanksTwitterShare
import com.kickstarter.libs.rx.transformers.Transformers
import com.kickstarter.libs.utils.UrlUtils.appendRefTag
import com.kickstarter.libs.utils.extensions.addToDisposable
import com.kickstarter.models.Project
import com.kickstarter.ui.data.CheckoutData
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject

interface ThanksShareHolderViewModel {
    interface Inputs {
        /** Call to configure the view model with a project.  */
        fun configureWith(thanksShareData: Pair<Project, CheckoutData>)

        /** Call when the share button is clicked.  */
        fun shareClick()

        /** Call when the share on Facebook button is clicked.  */
        fun shareOnFacebookClick()

        /** Call when the share on Twitter button is clicked.  */
        fun shareOnTwitterClick()

        fun onCleared()
    }

    interface Outputs {
        /** Emits the backing's project name.  */
        fun projectName(): Observable<String>

        /** Emits the project name and url to share using Android's default share behavior.  */
        fun startShare(): Observable<Pair<String, String>>

        /** Emits the project and url to share using Facebook.  */
        fun startShareOnFacebook(): Observable<Pair<Project, String>>

        /** Emits the project name and url to share using Twitter.  */
        fun startShareOnTwitter(): Observable<Pair<String, String>>
    }

    class ThanksShareViewHolderViewModel(environment: Environment) : Inputs, Outputs {

        private val thanksShareData = PublishSubject.create<Pair<Project, CheckoutData>>()
        private val project = PublishSubject.create<Project>()
        private val shareClick = PublishSubject.create<Unit>()
        private val shareOnFacebookClick = PublishSubject.create<Unit>()
        private val shareOnTwitterClick = PublishSubject.create<Unit>()
        private val projectName = BehaviorSubject.create<String>()
        private val startShare = PublishSubject.create<Pair<String, String>>()
        private val startShareOnFacebook = PublishSubject.create<Pair<Project, String>>()
        private val startShareOnTwitter = PublishSubject.create<Pair<String, String>>()

        val inputs: Inputs = this
        val outputs: Outputs = this

        private var disposables = CompositeDisposable()

        init {
            // Emit project and project name whenever configuration happens
            thanksShareData
                .map { it.first }
                .doOnNext { projectName.onNext(it.name()) }
                .subscribe { project.onNext(it) }
                .addToDisposable(disposables)

            // Share via default Android share
            project
                .map { Pair.create(it.name(), appendRefTag(it.webProjectUrl(), thanksShare().tag())) }
                .compose(Transformers.takeWhenV2(shareClick))
                .subscribe { startShare.onNext(it) }
                .addToDisposable(disposables)

            // Share on Facebook
            project
                .map { Pair.create(it, appendRefTag(it.webProjectUrl(), thanksFacebookShare().tag())) }
                .compose(Transformers.takeWhenV2(shareOnFacebookClick))
                .subscribe { startShareOnFacebook.onNext(it) }
                .addToDisposable(disposables)

            // Share on Twitter
            project
                .map { Pair.create(it.name(), appendRefTag(it.webProjectUrl(), thanksTwitterShare().tag())) }
                .compose(Transformers.takeWhenV2(shareOnTwitterClick))
                .subscribe { startShareOnTwitter.onNext(it) }
                .addToDisposable(disposables)
        }

        override fun configureWith(thanksShareData: Pair<Project, CheckoutData>) {
            this.thanksShareData.onNext(thanksShareData)
        }

        override fun shareClick() {
            shareClick.onNext(Unit)
        }

        override fun shareOnFacebookClick() {
            shareOnFacebookClick.onNext(Unit)
        }

        override fun shareOnTwitterClick() {
            shareOnTwitterClick.onNext(Unit)
        }

        override fun startShare(): Observable<Pair<String, String>> = startShare
        override fun startShareOnFacebook(): Observable<Pair<Project, String>> = startShareOnFacebook
        override fun startShareOnTwitter(): Observable<Pair<String, String>> = startShareOnTwitter
        override fun projectName(): Observable<String> = projectName

        override fun onCleared() {
            disposables.clear()
        }
    }
}
