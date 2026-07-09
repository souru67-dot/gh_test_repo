package com.souru.lumina.data.billing

import com.souru.lumina.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 「Proを解放しているか」を一元的に表す。判定は次のORで、オフラインや
 * Billing照会前でも直近の権利を維持する:
 *   - デバッグ専用オーバーライド(リリースでは常にfalse)
 *   - Play Billingが報告する購入済み
 *   - ローカルキャッシュ(直近にBillingが購入済みと報告した値)
 *
 * オンラインでBillingが「未所有」を報告するとキャッシュもfalseへ同期する
 * (払い戻し等の反映)。サーバ検証は行わない端末内完結アプリのため、この
 * 範囲の堅牢性(クロック/キャッシュ改ざんに対する脆弱性)は許容する。
 */
class EntitlementRepository(
    private val billing: BillingRepository,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
    private val isDebugBuild: Boolean,
) {
    val isPro: StateFlow<Boolean> = combine(
        billing.purchased,
        settings.proPurchasedCache,
        settings.proDebugOverride,
    ) { billed, cached, debugOverride ->
        (isDebugBuild && debugOverride) || billed || cached
    }.stateIn(scope, SharingStarted.Eagerly, false)

    /** 価格表示用の現地通貨フォーマット済み価格(未取得はnull)。 */
    val formattedPrice: StateFlow<String?> = billing.productDetails
        .map { it?.oneTimePurchaseOfferDetails?.formattedPrice }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val billingConnected: StateFlow<Boolean> = billing.connected

    init {
        // Billingが報告する購入済みをローカルキャッシュへ同期(オフライン維持用)
        scope.launch {
            billing.purchased.collect { settings.setProPurchasedCache(it) }
        }
    }

    /** 起動時・onResume時・「復元」ボタンから購入照会する。 */
    fun refresh() {
        billing.start()
        scope.launch { billing.queryPurchases() }
    }

    fun launchPurchase(activity: android.app.Activity) = billing.launchPurchase(activity)

    /** デバッグ専用: Pro状態を手動で切り替える。 */
    suspend fun setDebugOverride(enabled: Boolean) = settings.setProDebugOverride(enabled)
}
