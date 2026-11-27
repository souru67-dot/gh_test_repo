package com.souru.koyomi.data.billing

import android.app.Activity
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.souru.koyomi.data.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One-shot outcomes the paywall turns into a short message. */
enum class BillingEvent {
    /** Play Billing could not be reached (no Play Store, offline...). */
    UNAVAILABLE,
    /** A user-requested restore found the unlock. */
    RESTORED,
    /** A user-requested restore found nothing to restore. */
    NOTHING_TO_RESTORE,
    /** Purchase accepted but awaiting payment (e.g. convenience store). */
    PENDING,
    /** Purchase completed. */
    PURCHASED,
    /** Anything else that went wrong in the purchase flow. */
    ERROR,
}

/**
 * こよみ プレミアム — a single one-time unlock ("premium_unlock" in Play).
 * The entitlement is mirrored into DataStore so gates work offline and in
 * the widget process; Play remains the source of truth and re-syncs on app
 * start and after purchase/restore.
 */
class BillingRepository(private val context: Context) : PurchasesUpdatedListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val premiumKey = booleanPreferencesKey("premium_unlocked")

    /** Whether こよみ プレミアム is unlocked (cached; survives offline). */
    val isPremium: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[premiumKey] ?: false
    }

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)

    /** Play listing for the unlock (price text for the paywall), when loaded. */
    val productDetails: StateFlow<ProductDetails?> = _productDetails

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)

    /** Feedback for user-initiated purchase/restore. */
    val events: SharedFlow<BillingEvent> = _events

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** Blocks waiting for the connection; queued so concurrent callers share one. */
    private val pending = mutableListOf<Pair<() -> Unit, () -> Unit>>()
    private var connecting = false

    private fun withConnection(onFailure: () -> Unit = {}, block: () -> Unit) {
        if (client.isReady) {
            block()
            return
        }
        synchronized(pending) {
            pending += block to onFailure
            if (connecting) return
            connecting = true
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                val waiting = synchronized(pending) {
                    connecting = false
                    pending.toList().also { pending.clear() }
                }
                val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                waiting.forEach { (onOk, onFail) -> if (ok) onOk() else onFail() }
            }

            override fun onBillingServiceDisconnected() {
                val waiting = synchronized(pending) {
                    connecting = false
                    pending.toList().also { pending.clear() }
                }
                waiting.forEach { (_, onFail) -> onFail() }
            }
        })
    }

    /**
     * Re-checks ownership and loads the product listing. Called on app start;
     * [userInitiated] (the paywall's restore button) reports the outcome.
     */
    fun refresh(userInitiated: Boolean = false) {
        withConnection(
            onFailure = { if (userInitiated) _events.tryEmit(BillingEvent.UNAVAILABLE) },
        ) {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    val owned = handlePurchases(purchases)
                    if (userInitiated) {
                        _events.tryEmit(
                            if (owned) BillingEvent.RESTORED else BillingEvent.NOTHING_TO_RESTORE,
                        )
                    }
                } else if (userInitiated) {
                    _events.tryEmit(BillingEvent.UNAVAILABLE)
                }
            }
            queryProductDetails()
        }
    }

    private fun queryProductDetails(onLoaded: (ProductDetails?) -> Unit = {}) {
        client.queryProductDetailsAsync(
            QueryProductDetailsParams.newBuilder()
                .setProductList(
                    listOf(
                        QueryProductDetailsParams.Product.newBuilder()
                            .setProductId(PRODUCT_ID)
                            .setProductType(BillingClient.ProductType.INAPP)
                            .build(),
                    ),
                )
                .build(),
        ) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _productDetails.value = details.firstOrNull()
            }
            onLoaded(_productDetails.value)
        }
    }

    /**
     * Opens the Play purchase sheet, loading the listing first if needed.
     * Previously a tap before the listing had loaded silently did nothing.
     */
    fun purchase(activity: Activity) {
        withConnection(onFailure = { _events.tryEmit(BillingEvent.UNAVAILABLE) }) {
            val cached = _productDetails.value
            if (cached != null) {
                launchFlow(activity, cached)
            } else {
                queryProductDetails { details ->
                    if (details != null) {
                        activity.runOnUiThread { launchFlow(activity, details) }
                    } else {
                        _events.tryEmit(BillingEvent.UNAVAILABLE)
                    }
                }
            }
        }
    }

    private fun launchFlow(activity: Activity, details: ProductDetails) {
        if (activity.isFinishing) return
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build(),
                    ),
                )
                .build(),
        )
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> Unit
            // Bought earlier (another device / before a reinstall): just re-sync.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(userInitiated = true)
            else -> _events.tryEmit(BillingEvent.ERROR)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                val list = purchases.orEmpty()
                val owned = handlePurchases(list, fromPurchaseFlow = true)
                when {
                    owned -> _events.tryEmit(BillingEvent.PURCHASED)
                    list.any { it.purchaseState == Purchase.PurchaseState.PENDING } ->
                        _events.tryEmit(BillingEvent.PENDING)
                }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh(userInitiated = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> _events.tryEmit(BillingEvent.ERROR)
        }
    }

    /**
     * Acknowledges new purchases and mirrors ownership into DataStore.
     * Returns whether the unlock is owned. A purchase-flow callback only
     * carries the purchase just made, so it may grant but never revoke —
     * revocation (refunds) comes from the full [refresh] query.
     */
    private fun handlePurchases(
        purchases: List<Purchase>,
        fromPurchaseFlow: Boolean = false,
    ): Boolean {
        val owned = purchases.any { purchase ->
            PRODUCT_ID in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        purchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .filterNot { it.isAcknowledged }
            .forEach { purchase ->
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build(),
                ) { }
            }
        // Debug builds keep a manually granted unlock (there is no Play
        // purchase to find on a sideloaded build) so testers aren't reset to
        // the free tier on every cold start.
        val mayRevoke = !fromPurchaseFlow && !com.souru.koyomi.BuildConfig.DEBUG
        if (owned || mayRevoke) scope.launch { setPremium(owned) }
        return owned
    }

    private suspend fun setPremium(value: Boolean) {
        context.dataStore.edit { it[premiumKey] = value }
    }

    /** Debug builds only: flip the entitlement to test both sides of gates. */
    suspend fun debugSetPremium(value: Boolean) {
        setPremium(value)
    }

    companion object {
        const val PRODUCT_ID = "premium_unlock"
    }
}
