package com.lipabill.app

import java.io.File

fun resolveRepoFile(relative: String): File? {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
    repeat(6) {
        val candidate = File(dir, relative)
        if (candidate.exists()) return candidate
        dir = dir?.parentFile
    }
    return null
}
