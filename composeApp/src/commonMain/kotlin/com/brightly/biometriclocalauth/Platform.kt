package com.brightly.biometriclocalauth

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform