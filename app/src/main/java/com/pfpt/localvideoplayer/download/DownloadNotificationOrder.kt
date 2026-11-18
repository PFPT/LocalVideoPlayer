package com.pfpt.localvideoplayer.download

fun notificationTasksInStableOrder(tasks: Iterable<DownloadTask>): List<DownloadTask> =
    tasks.sortedWith(compareByDescending<DownloadTask> { it.createdAt }.thenBy { it.id })

fun notificationSortKey(createdAt: Long, id: String): String {
    val timeRank = (Long.MAX_VALUE - createdAt).toString().padStart(20, '0')
    return "$timeRank:$id"
}
