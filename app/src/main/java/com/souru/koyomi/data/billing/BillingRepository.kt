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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    private fun withConnection(block: () -> Unit) {
        if (client.isReady) {
            block()
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) block()
            }

            override fun onBillingServiceDisconnected() = Unit
        })
    }

    /** Re-checks ownership and loads the product listing. Call on app start. */
    fun refresh() {
        withConnection {
            client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    handlePurchases(purchases)
                }
            }
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
            }
        }
    }

    /** Opens the Play purchase sheet. */
    fun purchase(activity: Activity) {
        val details = _productDetails.value
        if (details == null) {
            refresh()
            return
        }
        withConnection {
            client.launchBillingFlow(
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
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            handlePurchases(purchases.orEmpty())
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
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
        scope.launch { setPremium(owned) }
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
