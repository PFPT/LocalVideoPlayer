package com.pfpt.localvideoplayer.util

internal fun nameSimilarity(rawA: String, rawB: String): Double {
    val a = TitleParser.normalizeForGrouping(rawA)
    val b = TitleParser.normalizeForGrouping(rawB)
    if (a == b) return 1.0
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a.length < SeriesGrouper.MIN_GROUP_KEY_LENGTH || b.length < SeriesGrouper.MIN_GROUP_KEY_LENGTH) return 0.0

    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a

    if (longer.startsWith(shorter)) {
        return shorter.length.toDouble() / longer.length
    }

    val prefixLen = a.commonPrefixWith(b).length
    val prefixRatio = prefixLen.toDouble() / shorter.length
    val tokenScore = tokenJaccard(a, b)
    val editScore = levenshteinRatio(a, b)
    val charScore = if (hasCjk(a) || hasCjk(b)) charBigramJaccard(a, b) else 0.0

    return maxOf(editScore, tokenScore, prefixRatio, charScore)
}

internal fun hasCjk(text: String): Boolean =
    text.any { it.code in 0x2E80..0x9FFF }

internal fun tokenJaccard(a: String, b: String): Double {
    val tokensA = a.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
    val tokensB = b.split(Regex("\\s+")).filter { it.length >= 2 }.toSet()
    if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
    val intersection = tokensA.intersect(tokensB).size
    val union = tokensA.union(tokensB).size
    return intersection.toDouble() / union
}

internal fun charBigramJaccard(a: String, b: String): Double {
    val compactA = a.replace(" ", "")
    val compactB = b.replace(" ", "")
    if (compactA == compactB) return 1.0
    if (compactA.length < 2 || compactB.length < 2) {
        return levenshteinRatio(compactA, compactB)
    }
    val bigramsA = (0 until compactA.length - 1).map { compactA.substring(it, it + 2) }.toSet()
    val bigramsB = (0 until compactB.length - 1).map { compactB.substring(it, it + 2) }.toSet()
    val intersection = bigramsA.intersect(bigramsB).size
    val union = bigramsA.union(bigramsB).size
    return intersection.toDouble() / union
}

internal fun levenshteinRatio(a: String, b: String): Double {
    val maxLen = maxOf(a.length, b.length)
    if (maxLen == 0) return 1.0
    return 1.0 - levenshteinDistance(a, b).toDouble() / maxLen
}

internal fun levenshteinDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    val prev = IntArray(b.length + 1) { it }
    val curr = IntArray(b.length + 1)

    for (i in a.indices) {
        curr[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            curr[j + 1] = minOf(
                curr[j] + 1,
                prev[j + 1] + 1,
                prev[j] + cost,
            )
        }
        prev.indices.forEach { idx -> prev[idx] = curr[idx] }
    }
    return prev[b.length]
}

internal fun clusterIndices(
    indices: List<Int>,
    shouldMerge: (Int, Int) -> Boolean,
): List<List<Int>> {
    if (indices.size < 2) return listOf(indices)

    val parent = IntArray(indices.size) { it }

    fun find(i: Int): Int {
        var root = i
        while (parent[root] != root) {
            parent[root] = parent[parent[root]]
            root = parent[root]
        }
        return root
    }

    fun union(i: Int, j: Int) {
        val rootI = find(i)
        val rootJ = find(j)
        if (rootI != rootJ) parent[rootJ] = rootI
    }

    for (i in indices.indices) {
        for (j in i + 1 until indices.size) {
            if (shouldMerge(indices[i], indices[j])) {
                union(i, j)
            }
        }
    }

    return indices.indices
        .groupBy { find(it) }
        .values
        .map { positions -> positions.map { indices[it] } }
}

