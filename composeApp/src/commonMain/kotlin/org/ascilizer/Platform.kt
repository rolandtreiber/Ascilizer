package org.ascilizer

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform