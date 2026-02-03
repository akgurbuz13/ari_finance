package com.ova.platform.shared.model

enum class Region(val code: String) {
    TR("TR"),
    EU("EU");

    companion object {
        fun fromCode(code: String): Region =
            entries.firstOrNull { it.code == code }
                ?: throw IllegalArgumentException("Unknown region: $code")
    }
}
