package com.zhaoshuo.localvideoplayer.download

enum class DownloadListStatusFilter {
    All,
    Active,
    Completed,
    Failed,
}

fun filterDownloadTasks(
    tasks: List<DownloadTask>,
    query: String,
    status: DownloadListStatusFilter,
): List<DownloadTask> {
    val needle = query.trim()
    return tasks.filter { task ->
        task.matchesStatus(status) &&
            (needle.isEmpty() || task.fileName.contains(needle, ignoreCase = true))
    }
}

private fun DownloadTask.matchesStatus(status: DownloadListStatusFilter): Boolean = when (status) {
    DownloadListStatusFilter.All -> true
    DownloadListStatusFilter.Active ->
        isMerging ||
            this.status == DownloadStatus.QUEUED ||
            this.status == DownloadStatus.RUNNING ||
            this.status == DownloadStatus.PAUSED
    DownloadListStatusFilter.Completed -> this.status == DownloadStatus.COMPLETED
    DownloadListStatusFilter.Failed ->
        this.status == DownloadStatus.FAILED || this.status == DownloadStatus.CANCELED
}
