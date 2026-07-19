package com.souru.colorhunt.domain.config

/**
 * Phase 4 seam. Everything that will eventually be gated behind a paid tier
 * reads [isPro] today so the branches already exist. In the MVP this is a
 * hard-coded free tier; Phase 4 will back it with Play Billing.
 *
 * Free vs Pro (see spec):
 *  - Free : export carries a watermark, only a few SNS sizes, capped collage cell count.
 *  - Pro  : watermark removed, all sizes + custom, extra templates, high-res map export,
 *           unlimited collage cells.
 */
object FeatureFlags {

    /** Hard-coded for the MVP. Phase 4 replaces this with a billing-backed value. */
    val isPro: Boolean = false

    /** Free tier caps collage cells; Pro is unlimited. Used as a gate today. */
    const val FREE_MAX_COLLAGE_CELLS = 9
}
