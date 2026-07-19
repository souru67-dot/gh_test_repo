package com.souru.colorhunt.ui.common

/** Format a packed ARGB colour int as an uppercase "#RRGGBB" hex code. */
fun Int.toHexCode(): String = "#%06X".format(0xFFFFFF and this)
