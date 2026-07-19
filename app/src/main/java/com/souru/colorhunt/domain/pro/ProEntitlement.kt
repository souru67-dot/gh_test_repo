package com.souru.colorhunt.domain.pro

import com.souru.colorhunt.domain.config.FeatureFlags

/**
 * Phase 4 seam — Pro entitlement source.
 *
 * The MVP resolves entitlement from the hard-coded [FeatureFlags.isPro]. Phase 4
 * will add a Play Billing backed implementation and swap it in here without the
 * feature-gating call sites (collage sizes/cells, watermark, map export) having
 * to change — they already branch on an `isPro` boolean.
 */
interface ProEntitlement {
    val isPro: Boolean
}

/** Default MVP entitlement: reads the static flag (always free today). */
object FlagProEntitlement : ProEntitlement {
    override val isPro: Boolean get() = FeatureFlags.isPro
}

/**
 * Phase 4 stub for Play Billing. Intentionally empty: it compiles and marks the
 * exact spot where BillingClient wiring will live (query purchases, acknowledge,
 * expose an `isPro` StateFlow). Until then it reports the free tier.
 */
object PlayBillingEntitlement : ProEntitlement {
    override val isPro: Boolean get() = false

    fun connect() {
        // TODO(Phase 4): start BillingClient, query INAPP/SUBS purchases,
        //  acknowledge, and drive `isPro`.
    }

    fun purchasePro() {
        // TODO(Phase 4): launch the billing flow for the Pro product.
    }
}
