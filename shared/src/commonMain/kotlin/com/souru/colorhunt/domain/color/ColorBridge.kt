package com.souru.colorhunt.domain.color

/**
 * Swift-friendly helpers over the colour domain (see [CollageBridgeNote] in
 * config for the rationale — enum bridging is awkward in ObjC interop).
 */
object ColorBridge {

    /** All buckets in display order. Swift receives an NSArray of ColorBucket. */
    fun buckets(): List<ColorBucket> = ColorBucket.entries

    /** Stable key for localisation lookups on iOS (e.g. "RED", "YELLOW_GREEN"). */
    fun keyOf(bucket: ColorBucket): String = bucket.name

    /** Classify a packed 0xAARRGGBB colour and return the bucket's stable key. */
    fun classifyKey(colorInt: Int): String = ColorClassifier.classify(colorInt).name

    /** Representative swatch (0xFFRRGGBB) for a bucket key; white if unknown. */
    fun swatchOf(key: String): Int =
        ColorBucket.entries.firstOrNull { it.name == key }?.swatch ?: 0xFFFFFFFF.toInt()
}
