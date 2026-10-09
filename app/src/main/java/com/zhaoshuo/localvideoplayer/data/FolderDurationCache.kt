package com.zhaoshuo.localvideoplayer.data

import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class FolderDurationCache(private val file: File) {
    private val durations = ConcurrentHashMap<String, Long>()
    @Volatile private var dirty = false

    fun load() {
        if (!file.exists()) return
        runCatching {
            val json = JSONObject(file.readText())
            durations.clear()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                durations[key] = json.getLong(key)
            }
        }
        dirty = false
    }

    fun get(uri: String, modifiedMs: Long): Long? = durations[cacheKey(uri, modifiedMs)]

    fun put(uri: String, modifiedMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        val key = cacheKey(uri, modifiedMs)
        if (durations[key] == durationMs) return
        durations[key] = durationMs
        dirty = true
    }

    fun save() {
        if (!dirty) return
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val json = JSONObject()
        durations.forEach { (key, value) -> json.put(key, value) }
        val temp = File(parent, "${file.name}.tmp")
        temp.writeText(json.toString())
        if (!temp.renameTo(file)) {
            file.writeText(json.toString())
            temp.delete()
        }
        dirty = false
    }

    companion object {
        fun cacheKey(uri: String, modifiedMs: Long): String = "$modifiedMs|$uri"
    }
}
