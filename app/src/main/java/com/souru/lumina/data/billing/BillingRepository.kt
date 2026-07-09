package com.souru.lumina.data.billing

import android.app.Activity
import android.content.Context
import android.util.Log
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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Google Play Billing のラッパ。Pro解除は非消費型(1回購入)アイテム [PRODUCT_ID]。
 *
 * - 起動/復帰時に [queryPurchases] で購入照会(復元)
 * - Pending購入は「まだ未付与」として扱い、確定(PURCHASED)時に承認する
 * - 同一Googleアカウントの別端末でも queryPurchases で復元される
 *
 * サーバ側レシート検証は行わない(端末内完結アプリのため)。その限界は許容。
 */
class BillingRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _purchased = MutableStateFlow(false)
    /** Proが購入済み(かつ承認済み or 承認処理中の PURCHASED)か。 */
    val purchased: StateFlow<Boolean> = _purchased.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    /** 価格表示用の商品詳細(現地価格は formattedPrice から取得)。 */
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch { purchases.forEach { handlePurchase(it) } }
        } else if (result.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            Log.i(TAG, "購入がキャンセルされました")
        } else {
            Log.w(TAG, "購入更新エラー: ${result.responseCode} ${result.debugMessage}")
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
        )
        .build()

    /** 接続を開始し、切断時は再接続する。冪等。 */
    fun start() {
        if (client.connectionState == BillingClient.ConnectionState.CONNECTED ||
            client.connectionState == BillingClient.ConnectionState.CONNECTING
        ) {
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _connected.value = true
                    scope.launch {
                        queryProductDetails()
                        queryPurchases()
                    }
                } else {
                    Log.w(TAG, "Billing接続失敗: ${result.responseCode} ${result.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                _connected.value = false // 次回操作時に start() で再接続
            }
        })
    }

    /** 購入照会(復元)。起動時・onResume時・「復元」ボタンから呼ぶ。 */
    suspend fun queryPurchases() {
        if (client.connectionState != BillingClient.ConnectionState.CONNECTED) {
            start()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val owns = result.purchasesList.any { purchaseGrantsPro(it) }
            result.purchasesList.forEach { handlePurchase(it) }
            // 所有していなければ false に落とす(別端末での払い戻し反映等)
            if (!owns) _purchased.value = false
        } else {
            Log.w(TAG, "購入照会失敗: ${result.billingResult.responseCode}")
        }
    }

    private suspend fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            _productDetails.value = result.productDetailsList?.firstOrNull()
        } else {
            Log.w(TAG, "商品詳細取得失敗: ${result.billingResult.responseCode}")
        }
    }

    /** 購入フローを起動する。商品詳細が未取得なら取得を試みてからでないと呼べない。 */
    fun launchPurchase(activity: Activity) {
        val details = _productDetails.value ?: run {
            Log.w(TAG, "商品詳細が未取得のため購入を開始できません")
            scope.launch { queryProductDetails() }
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .build()
        val flowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        client.launchBillingFlow(activity, flowParams)
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                if (!purchaseGrantsPro(purchase)) return
                _purchased.value = true
                if (!purchase.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                    val result = client.acknowledgePurchase(ack)
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        Log.w(TAG, "購入承認失敗: ${result.responseCode}")
                    }
                }
            }

            Purchase.PurchaseState.PENDING ->
                Log.i(TAG, "購入がPending(未確定)。確定時に付与されます")

            else -> Unit
        }
    }

    private fun purchaseGrantsPro(purchase: Purchase): Boolean =
        purchase.products.contains(PRODUCT_ID)

    companion object {
        private const val TAG = "BillingRepository"

        /** Play Console で作成する非消費型アイテムのID(差し替え時はREADME参照)。 */
        const val PRODUCT_ID = "lumina_pro"
    }
}
