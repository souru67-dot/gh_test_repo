package com.souru.colorhunt.data.billing

import android.app.Activity
import android.content.Context
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
import com.souru.colorhunt.domain.pro.ProState

/**
 * Play Billing (Phase 4) — unlocks the Pro tier.
 *
 * Connects on app start, queries existing purchases (so entitlement survives
 * reinstalls), and drives [ProState] which [com.souru.colorhunt.domain.config.FeatureFlags]
 * reads. The product is a one-time (INAPP) unlock. All callbacks fail safe; if
 * billing is unavailable the app simply stays on the free tier.
 *
 * Requires a matching managed product (id [PRO_PRODUCT_ID]) configured in the
 * Play Console and a signed build to actually complete a purchase.
 */
class BillingManager(context: Context) : PurchasesUpdatedListener, BillingClientStateListener {

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    @Volatile
    private var productDetails: ProductDetails? = null

    val hasProduct: Boolean get() = productDetails != null

    fun connect() {
        if (client.connectionState != BillingClient.ConnectionState.CONNECTED) {
            runCatching { client.startConnection(this) }
        }
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            queryOwnedPurchases()
            queryProduct()
        }
    }

    override fun onBillingServiceDisconnected() {
        // Left to the next connect(); no aggressive retry loop for the MVP.
    }

    private fun queryProduct() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        client.queryProductDetailsAsync(params) { _, detailsList ->
            productDetails = detailsList.firstOrNull()
        }
    }

    private fun queryOwnedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { _, purchases ->
            val owned = purchases.any { it.isPro() }
            ProState.update(owned)
            purchases.forEach { acknowledgeIfNeeded(it) }
        }
    }

    /** Launch the Google Play purchase flow. No-op if product details aren't loaded yet. */
    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: return
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .build(),
                ),
            )
            .build()
        runCatching { client.launchBillingFlow(activity, params) }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            purchases.forEach { purchase ->
                if (purchase.isPro()) {
                    ProState.update(true)
                    acknowledgeIfNeeded(purchase)
                }
            }
        }
    }

    private fun acknowledgeIfNeeded(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED && !purchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            client.acknowledgePurchase(params) { /* best-effort */ }
        }
    }

    private fun Purchase.isPro(): Boolean =
        purchaseState == Purchase.PurchaseState.PURCHASED && products.contains(PRO_PRODUCT_ID)

    companion object {
        /** Managed product id — must match the Play Console entry. */
        const val PRO_PRODUCT_ID = "colorhunt_pro"
    }
}
