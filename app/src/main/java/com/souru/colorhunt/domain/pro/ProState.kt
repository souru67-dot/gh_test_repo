package com.souru.colorhunt.domain.pro

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for the Pro entitlement at runtime (Phase 4).
 *
 * The billing layer ([com.souru.colorhunt.data.billing.BillingManager]) updates
 * it after querying purchases / completing a purchase; feature gates read it
 * through [com.souru.colorhunt.domain.config.FeatureFlags], and reactive screens
 * can observe [isPro].
 */
object ProState {
    private val _isPro = MutableStateFlow(false)
    val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    val isProNow: Boolean get() = _isPro.value

    fun update(value: Boolean) {
        _isPro.value = value
    }
}
