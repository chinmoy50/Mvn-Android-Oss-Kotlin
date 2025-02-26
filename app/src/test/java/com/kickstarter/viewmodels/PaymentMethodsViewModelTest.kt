package com.kickstarter.viewmodels

import android.util.Pair
import com.kickstarter.DeletePaymentSourceMutation
import com.kickstarter.KSRobolectricTestCase
import com.kickstarter.libs.Environment
import com.kickstarter.mock.factories.StoredCardFactory
import com.kickstarter.mock.services.MockApolloClientV2
import com.kickstarter.models.Project
import com.kickstarter.models.StoredCard
import com.kickstarter.services.mutations.SavePaymentMethodData
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subscribers.TestSubscriber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import java.util.Collections

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalStdlibApi::class)
class PaymentMethodsViewModelTest : KSRobolectricTestCase() {

    private lateinit var vm: PaymentMethodsViewModel

    private val cards = TestSubscriber<List<StoredCard>>()
    private val dividerIsVisible = TestSubscriber<Boolean>()
    private val error = TestSubscriber<String>()
    private val progressBarIsVisible = TestSubscriber<Boolean>()
    private val showDeleteCardDialog = TestSubscriber<Unit>()
    private val successDeleting = TestSubscriber<String>()
    private val presentPaymentSheet = TestSubscriber<Pair<String, String>>()
    private val showError = TestSubscriber<String>()
    private val successSaving = TestSubscriber<String>()
    private val compositeDisposable = CompositeDisposable()

    private fun setUpEnvironment(environment: Environment, dispatcher: CoroutineDispatcher? = null) {

        this.vm = PaymentMethodsViewModel.Factory(environment, dispatcher).create(PaymentMethodsViewModel::class.java)

        compositeDisposable.add(this.vm.outputs.error().subscribe { this.error.onNext(it) })
        compositeDisposable.add(this.vm.outputs.cards().subscribe { this.cards.onNext(it) })
        compositeDisposable.add(this.vm.outputs.dividerIsVisible().subscribe { this.dividerIsVisible.onNext(it) })
        compositeDisposable.add(this.vm.outputs.progressBarIsVisible().subscribe { this.progressBarIsVisible.onNext(it) })
        compositeDisposable.add(this.vm.outputs.showDeleteCardDialog().subscribe { this.showDeleteCardDialog.onNext(it) })
        compositeDisposable.add(this.vm.outputs.successDeleting().subscribe { this.successDeleting.onNext(it) })
        compositeDisposable.add(this.vm.outputs.presentPaymentSheet().subscribe { this.presentPaymentSheet.onNext(it) })
        compositeDisposable.add(this.vm.outputs.showError().subscribe { this.showError.onNext(it) })
        compositeDisposable.add(this.vm.outputs.successSaving().subscribe { this.successSaving.onNext(it) })
    }

    @After
    fun cleanUp() {
        compositeDisposable.clear()
    }

    @Test
    fun testRefreshCards_rx() {
        setUpEnvironment(environment())
        this.vm.inputs.refreshCards()
        this.cards.assertValueCount(2)
    }

    @Test
    fun testRefreshCards() = runTest {
        val dispatcher = coroutineContext[CoroutineDispatcher]

        setUpEnvironment(
            environment(),
            dispatcher
        )

        this@PaymentMethodsViewModelTest.vm.inputs.refreshCards()

        advanceUntilIdle()

        this@PaymentMethodsViewModelTest.cards.assertValueCount(2)
    }

    @Test
    fun testCards() = runTest {
        val dispatcher = coroutineContext[CoroutineDispatcher]

        val card = StoredCardFactory.discoverCard()

        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override suspend fun _getStoredCards(): Result<List<StoredCard>> {
                    delay(1000)
                    return Result.success(Collections.singletonList(card))
                }
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.just(Collections.singletonList(card))
                }
            }).build(),
            dispatcher
        )

        advanceUntilIdle()

        this@PaymentMethodsViewModelTest.cards.assertValue(Collections.singletonList(card))
    }

    @Test
    fun testDividerIsVisible_hasCards() {
        setUpEnvironment(environment())

        this.dividerIsVisible.assertValues(true)
    }

    @Test
    fun testDividerIsVisible_noCards() {
        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override suspend fun _getStoredCards(): Result<List<StoredCard>> {
                    return getStoredCards().runCatching { blockingSingle() }
                }
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.just(Collections.emptyList())
                }
            }).build()
        )

        this.dividerIsVisible.assertValues(false)
    }

    @Test
    fun testErrorGettingCards() {
        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override suspend fun _getStoredCards(): Result<List<StoredCard>> {
                    return getStoredCards().runCatching { blockingSingle() }
                }
                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return Observable.error(Exception("oops"))
                }
            }).build()
        )

        this.cards.assertNoValues()
        this.error.assertNoValues()
    }

    @Test
    fun testErrorDeletingCard() {
        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override fun deletePaymentSource(paymentSourceId: String): Observable<DeletePaymentSourceMutation.Data> {
                    return Observable.error(Throwable("eek"))
                }
            }).build()
        )

        this.vm.inputs.deleteCardClicked("id")
        this.vm.confirmDeleteCardClicked()
        this.error.assertValue("eek")
    }

    @Test
    fun testProgressBarIsVisible() {
        setUpEnvironment(environment())

        // getting the cards initially
        this.progressBarIsVisible.assertValues(false)
        this.vm.inputs.deleteCardClicked("id")
        this.vm.inputs.confirmDeleteCardClicked()
        // make the call to delete and reload the cards
        this.progressBarIsVisible.assertValues(false, true, false)
    }

    @Test
    fun testShowDeleteCardDialog() {
        setUpEnvironment(environment())

        this.vm.inputs.deleteCardClicked("5555")
        this.showDeleteCardDialog.assertValueCount(1)
    }

    @Test
    fun testSuccess() {
        setUpEnvironment(environment())

        this.cards.assertValueCount(1)
        this.vm.inputs.deleteCardClicked("id")
        this.vm.inputs.confirmDeleteCardClicked()
        this.successDeleting.assertValueCount(1)
        this.cards.assertValueCount(2)
    }

    @Test
    fun testPresentPaymentSheetSuccess() {
        val setupClientId = "seti_1KbABk4VvJ2PtfhKV8E7dvGe_secret_LHjfXxFl9UDucYtsL5a3WtySqjgqf5F"
        val email = "some@email.com"

        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override fun createSetupIntent(project: Project?): Observable<String> {
                    return Observable.just(setupClientId)
                }
            }).build()
        )

        this.vm.inputs.newCardButtonClicked()

        this.presentPaymentSheet.assertValue(Pair.create(setupClientId, email))
        this.progressBarIsVisible.assertValues(false, true, false, true, false)
        this.showError.assertNoValues()
    }

    @Test
    fun testPresentPaymentSheetError() {
        val errorString = "Something went wrong"
        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override fun createSetupIntent(project: Project?): Observable<String> {
                    return Observable.error(Exception(errorString))
                }
            }).build()
        )

        this.vm.inputs.newCardButtonClicked()

        this.presentPaymentSheet.assertNoValues()
        this.progressBarIsVisible.assertValues(false, true, false, true, false)
        this.showError.assertValue(errorString)
    }

    @Test
    fun testSavePaymentMethodSuccess() {
        val setupClientId = "seti_1KbABk4VvJ2PtfhKV8E7dvGe_secret_LHjfXxFl9UDucYtsL5a3WtySqjgqf5F"
        val email = "some@email.com"
        val card = StoredCardFactory.visa()
        val cardsList = listOf(StoredCardFactory.discoverCard())
        val cardsListUpdated = listOf(StoredCardFactory.discoverCard(), card)
        var numberOfCalls = 1

        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override fun createSetupIntent(project: Project?): Observable<String> {
                    return Observable.just(setupClientId)
                }

                override fun savePaymentMethod(savePaymentMethodData: SavePaymentMethodData): Observable<StoredCard> {
                    return Observable.just(card)
                }

                override suspend fun _getStoredCards(): Result<List<StoredCard>> {
                    return getStoredCards().runCatching { blockingSingle() }
                }

                override fun getStoredCards(): Observable<List<StoredCard>> {
                    if (numberOfCalls == 1) {
                        numberOfCalls++
                        return Observable.just(cardsList)
                    } else {
                        return Observable.just(cardsListUpdated)
                    }
                }
            }).build()
        )

        // - Button clicked
        this.vm.inputs.newCardButtonClicked()

        this.presentPaymentSheet.assertValue(Pair.create(setupClientId, email))
        this.progressBarIsVisible.assertValues(false, true, false, true, false)
        this.showError.assertNoValues()

        // - User added correct payment method to paymentSheet
        this.vm.inputs.savePaymentOption()
        this.cards.assertValueCount(2)
        this.cards.assertValues(cardsList, cardsListUpdated)
        this.progressBarIsVisible.assertValues(false, true, false, true, false, true, false, true, false)
        this.showError.assertNoValues()
        this.successSaving.assertValue(card.toString())
    }

    @Test
    fun testSavePaymentMethodError() {
        val setupClientId = "seti_1KbABk4VvJ2PtfhKV8E7dvGe_secret_LHjfXxFl9UDucYtsL5a3WtySqjgqf5F"
        val email = "some@email.com"
        val cardsList = listOf(StoredCardFactory.discoverCard())
        var numberOfCalls = 1
        val errorString = "Something went wrong"

        setUpEnvironment(
            environment().toBuilder().apolloClientV2(object : MockApolloClientV2() {
                override fun createSetupIntent(project: Project?): Observable<String> {
                    return Observable.just(setupClientId)
                }

                override fun savePaymentMethod(savePaymentMethodData: SavePaymentMethodData): Observable<StoredCard> {
                    return Observable.error(Exception(errorString))
                }

                override suspend fun _getStoredCards(): Result<List<StoredCard>> {
                    return getStoredCards().runCatching { blockingSingle() }
                }

                override fun getStoredCards(): Observable<List<StoredCard>> {
                    return if (numberOfCalls == 1) {
                        numberOfCalls++
                        Observable.just(cardsList)
                    } else {
                        Observable.error(Exception(errorString))
                    }
                }
            }).build()
        )

        // - Button clicked
        this.vm.inputs.newCardButtonClicked()

        this.presentPaymentSheet.assertValue((Pair.create(setupClientId, email)))
        this.progressBarIsVisible.assertValues(false, true, false, true, false)
        this.showError.assertNoValues()

        // - User added correct payment method using paymentSheet, but some error happen during the process
        this.vm.inputs.savePaymentOption()
        this.cards.assertValueCount(1)
        this.cards.assertValues(cardsList)
        this.progressBarIsVisible.assertValues(false, true, false, true, false, true, false, true, false)
        this.showError.assertValues(errorString)
        this.successSaving.assertNoValues()
    }
}
