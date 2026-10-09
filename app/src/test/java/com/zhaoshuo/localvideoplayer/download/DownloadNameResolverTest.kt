package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadNameResolverTest {
    @Test
    fun parsesTitleAndUrlFromPipeFormat() {
        val parsed = DownloadNameResolver.parseSharedText("说唱巅峰对决 2026-20260712 下|http://127.0.0.1:9978/m3u8?url=abc")
        assertEquals("说唱巅峰对决 2026-20260712 下", parsed.title)
        assertEquals("http://127.0.0.1:9978/m3u8?url=abc", parsed.url)
    }

    @Test
    fun parsesTitleAndUrlFromMultilineShare() {
        val parsed = DownloadNameResolver.parseSharedText(
            """
            说唱巅峰对决 2026-20260712 下
            http://127.0.0.1:9978/m3u8?url=abc
            """.trimIndent(),
        )
        assertEquals("说唱巅峰对决 2026-20260712 下", parsed.title)
        assertEquals("http://127.0.0.1:9978/m3u8?url=abc", parsed.url)
    }

    @Test
    fun parsesUrlOnlyShare() {
        val parsed = DownloadNameResolver.parseSharedText("https://cdn.example.com/movie.mp4")
        assertNull(parsed.title)
        assertEquals("https://cdn.example.com/movie.mp4", parsed.url)
    }

    @Test
    fun parsesUrlEmbeddedInClipboardText() {
        val parsed = DownloadNameResolver.parseSharedText("请复制：https://cdn.example.com/a.mp4")
        assertNull(parsed.title)
        assertEquals("https://cdn.example.com/a.mp4", parsed.url)
    }

    @Test
    fun parsesUppercaseHttpM3u8() {
        val parsed = DownloadNameResolver.parseSharedText("HTTP://cdn.example.com/video/index.m3u8")
        assertEquals("http://cdn.example.com/video/index.m3u8", parsed.url)
    }

    @Test
    fun parsesM3u8WithInvisiblePrefix() {
        val parsed = DownloadNameResolver.parseSharedText("\uFEFF\u200Bhttps://cdn.example.com/index.m3u8")
        assertEquals("https://cdn.example.com/index.m3u8", parsed.url)
    }

    @Test
    fun pasteMultilineShareExtractsUrl() {
        val change = DownloadNameResolver.applyDownloadFieldChange(
            current = "",
            incoming = "剧名\nhttps://cdn.example.com/index.m3u8\n",
        )
        assertEquals(true, change.pasted)
        assertEquals("剧名", change.title)
        assertEquals("https://cdn.example.com/index.m3u8", change.text)
    }

    @Test
    fun pasteWithoutUrlKeepsFlattenedText() {
        val change = DownloadNameResolver.applyDownloadFieldChange(
            current = "",
            incoming = "没有链接\n只有说明",
        )
        assertEquals(true, change.pasted)
        assertNull(change.title)
        assertEquals("没有链接只有说明", change.text)
    }

    @Test
    fun typingOneCharacterIsNotTreatedAsPaste() {
        val change = DownloadNameResolver.applyDownloadFieldChange(
            current = "https://cdn.example.com/index.m3u8",
            incoming = "https://cdn.example.com/index.m3u8x",
        )
        assertEquals(false, change.pasted)
        assertEquals("https://cdn.example.com/index.m3u8x", change.text)
    }

    @Test
    fun parsesMagnetFromClipboard() {
        val magnet = "magnet:?xt=urn:btih:aaabb331189c1bb1f198797dc208d14afa824429&dn=Show"
        val parsed = DownloadNameResolver.parseSharedText(magnet)
        assertNull(parsed.title)
        assertEquals(magnet, parsed.url)
    }

    @Test
    fun parsesFirstMagnetFromGluedClipboard() {
        val first = "magnet:?xt=urn:btih:aaabb331189c1bb1f198797dc208d14afa824429"
        val glued = first + "magnet:?xt=urn:btih:bbbbb331189c1bb1f198797dc208d14afa824429"
        val parsed = DownloadNameResolver.parseSharedText(glued)
        assertEquals(first, parsed.url)
    }

    @Test
    fun addsMp4ExtensionForHlsTitle() {
        assertEquals(
            "说唱巅峰对决.mp4",
            DownloadNameResolver.toOutputFileName("说唱巅峰对决", isHls = true, fallback = "download.mp4"),
        )
    }

    @Test
    fun sanitizesInvalidFileNameCharacters() {
        assertEquals("abc_def", DownloadNameResolver.sanitizeTitle("abc/def"))
    }

    @Test
    fun infersTitleFromCdnPath() {
        val title = DownloadNameResolver.inferTitleFromCdnPath(
            "https://v.lzcdn28.com/20260712/12602_8c511196/index.m3u8",
        )
        assertEquals("20260712-12602_8c511196", title)
    }

    @Test
    fun composesSeriesAndEpisodeFromFongMiMedia() {
        val title = DownloadNameResolver.composeFongMiTitle(
            DownloadNameResolver.FongMiMedia(title = "种地吧4", artist = "20260514上"),
            "http://127.0.0.1:9978/m3u8?url=https%3A%2F%2Fv.lzcdn28.com%2F20260514%2Findex.m3u8",
        )
        assertEquals("种地吧4-20260514上", title)
    }

    @Test
    fun composesTalkShowTitleFromFongMiMedia() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val title = DownloadNameResolver.composeFongMiTitle(
            DownloadNameResolver.FongMiMedia(
                title = "脱口秀和 Ta 的朋友们第三季",
                artist = "20260627下",
                url = downloadUrl,
            ),
            downloadUrl,
        )
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260627下", title)
    }

    @Test
    fun matchesNestedProxyDownloadUrlWithFongMiMediaUrl() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val mediaUrl =
            "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260627%2F11873_1c6e729b%2Findex.m3u8"
        assertEquals(true, DownloadNameResolver.mediaMatchesDownloadUrl(mediaUrl, downloadUrl))
    }

    @Test
    fun doesNotMatchDifferentStreamFromFongMiMediaUrl() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val mediaUrl =
            "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260712%2F12602_8c511196%2Findex.m3u8"
        assertEquals(false, DownloadNameResolver.mediaMatchesDownloadUrl(mediaUrl, downloadUrl))
    }

    @Test
    fun matchesDownloadByEpisodeDateWhenMediaUrlMissing() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val mediaUrl =
            "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260627%2F11873_1c6e729b%2Findex.m3u8"
        val media = DownloadNameResolver.FongMiMedia(
            title = "脱口秀和 Ta 的朋友们第三季",
            artist = "20260627下",
            url = mediaUrl,
        )
        assertEquals(true, DownloadNameResolver.mediaMatchesDownload(media, downloadUrl))
    }

    @Test
    fun doesNotMatchDifferentEpisodeDateWhenMediaUrlMissing() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val media = DownloadNameResolver.FongMiMedia(
            title = "脱口秀和 Ta 的朋友们第三季",
            artist = "20260712上",
            url = "",
        )
        assertEquals(false, DownloadNameResolver.mediaMatchesDownload(media, downloadUrl))
    }

    @Test
    fun matchesDownloadWithPlayUrlField() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val mediaUrl =
            "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260627%2F11873_1c6e729b%2Findex.m3u8"
        val media = DownloadNameResolver.FongMiMedia(
            title = "脱口秀和 Ta 的朋友们第三季",
            artist = "20260627下",
            url = mediaUrl,
        )
        assertEquals(true, DownloadNameResolver.mediaMatchesDownload(media, downloadUrl))
    }

    @Test
    fun infersCdnTitleForNestedProxyUrlWithoutMatchingMedia() {
        val title = DownloadNameResolver.inferTitleFromCdnPath(
            "https://v.lzcdn28.com/20260627/11873_1c6e729b/index.m3u8",
        )
        assertEquals("20260627-11873_1c6e729b", title)
    }

    @Test
    fun enrichesTitleWithDateFromStreamUrlWhenArtistMissing() {
        val title = DownloadNameResolver.enrichTitleFromStreamUrl(
            "种地吧4",
            "http://127.0.0.1:9978/m3u8?url=https%3A%2F%2Fv.lzcdn28.com%2F20260514%2Findex.m3u8",
        )
        assertEquals("种地吧4-20260514", title)
    }

    @Test
    fun prefersExplicitNameQueryParam() {
        val title = DownloadNameResolver.extractTitleFromUrl(
            "http://127.0.0.1:9978/m3u8?name=%E7%A7%8D%E5%9C%B0%E5%90%A74-20260514%E4%B8%8A&url=https%3A%2F%2Fexample.com%2Findex.m3u8",
        )
        assertEquals("种地吧4-20260514上", title)
    }

    @Test
    fun composesHistoryTitle() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val title = DownloadNameResolver.composeHistoryTitle(
            "脱口秀和 Ta 的朋友们第三季",
            "20260627下",
            downloadUrl,
        )
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260627下", title)
    }

    @Test
    fun rejectsCrossEpisodeMediaWhileDownloadingAnotherEpisode() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val playingEpisodeOne = DownloadNameResolver.FongMiMedia(
            title = "脱口秀和 Ta 的朋友们第三季",
            artist = "20260626上",
            url = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260626%2F11873_aaa%2Findex.m3u8",
        )
        assertEquals(false, DownloadNameResolver.mediaMatchesDownload(playingEpisodeOne, downloadUrl))
    }

    @Test
    fun resolvesCrossEpisodeTitleFromHistory() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val history = listOf(
            DownloadNameResolver.FongMiHistoryEntry(
                vodName = "脱口秀和 Ta 的朋友们第三季",
                vodRemarks = "20260626上",
                episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260626%2F11873_aaa%2Findex.m3u8",
            ),
            DownloadNameResolver.FongMiHistoryEntry(
                vodName = "脱口秀和 Ta 的朋友们第三季",
                vodRemarks = "20260627下",
                episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260627%2F11873_1c6e729b%2Findex.m3u8",
            ),
        )
        val title = DownloadNameResolver.resolveTitleFromHistory(history, downloadUrl)
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260627下", title)
    }

    @Test
    fun historyEntryMatchesDownloadByEpisodeUrl() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val entry = DownloadNameResolver.FongMiHistoryEntry(
            vodName = "脱口秀和 Ta 的朋友们第三季",
            vodRemarks = "20260627下",
            episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260627%2F11873_1c6e729b%2Findex.m3u8",
        )
        assertEquals(true, DownloadNameResolver.historyEntryMatchesDownload(entry, downloadUrl))
    }

    @Test
    fun parsesSubjectAndUrlShareText() {
        val parsed = DownloadNameResolver.parseSharedText(
            "脱口秀和 Ta 的朋友们第三季-20260627下|http://127.0.0.1:9978/m3u8?url=abc",
        )
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260627下", parsed.title)
        assertEquals("http://127.0.0.1:9978/m3u8?url=abc", parsed.url)
    }

    @Test
    fun normalizesPlayingStatusPrefix() {
        assertEquals("剧名", DownloadNameResolver.normalizeFongMiText("正在播放·剧名"))
        assertEquals("剧名", DownloadNameResolver.normalizeFongMiText("正在播放 剧名"))
    }

    @Test
    fun rejectsMismatchedEpisodeTitleForDownloadUrl() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        assertEquals(
            false,
            DownloadNameResolver.titleMatchesDownload("脱口秀和 Ta 的朋友们第三季-20260626上", downloadUrl),
        )
    }

    @Test
    fun rejectsMismatchedNameQueryParamForDownloadUrl() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?name=%E8%84%B1%E5%8F%A3%E7%A7%80%E5%92%8C%20Ta%20%E7%9A%84%E6%9C%8B%E5%8F%8B%E4%BB%AC%E7%AC%AC%E4%B8%89%E5%AD%A3-20260626%E4%B8%8A&url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val fromUrl = DownloadNameResolver.extractTitleFromUrl(downloadUrl)
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260626上", fromUrl)
        assertEquals(false, DownloadNameResolver.titleMatchesDownload(fromUrl!!, downloadUrl))
    }

    @Test
    fun rejectsCrossEpisodeMediaWithPlayingStatusTitle() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val media = DownloadNameResolver.FongMiMedia(
            title = "正在播放",
            artist = "脱口秀和 Ta 的朋友们第三季-20260626上",
            url = "",
        )
        assertEquals(false, DownloadNameResolver.mediaMatchesDownload(media, downloadUrl))
    }

    @Test
    fun composesTitleWithoutPlayingStatusNoise() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val title = DownloadNameResolver.composeFongMiTitle(
            DownloadNameResolver.FongMiMedia(
                title = "正在播放",
                artist = "脱口秀和 Ta 的朋友们第三季-20260627下",
            ),
            downloadUrl,
        )
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260627下", title)
    }

    @Test
    fun skipsPlaybackStatusLineInMultilineShare() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val parsed = DownloadNameResolver.parseSharedText(
            """
            正在播放
            脱口秀和 Ta 的朋友们第三季-20260627下
            $downloadUrl
            """.trimIndent(),
        )
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260627下", parsed.title)
        assertEquals(downloadUrl, parsed.url)
    }

    @Test
    fun historyEntryMatchesByCombinedVodNameAndRemarks() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260627%252F11873_1c6e729b%252Findex.m3u8"
        val entry = DownloadNameResolver.FongMiHistoryEntry(
            vodName = "脱口秀和 Ta 的朋友们第三季",
            vodRemarks = "20260627下",
            episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260627%2F11873_1c6e729b%2Findex.m3u8",
        )
        assertEquals(true, DownloadNameResolver.historyEntryMatchesDownload(entry, downloadUrl))
    }

    @Test
    fun rejectsSameDayDifferentHalfEpisodeHistoryMatch() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        val episodeThree = DownloadNameResolver.FongMiHistoryEntry(
            vodName = "脱口秀和 Ta 的朋友们第三季",
            vodRemarks = "20260712上",
            episodeUrl = "",
        )
        val episodeFour = DownloadNameResolver.FongMiHistoryEntry(
            vodName = "脱口秀和 Ta 的朋友们第三季",
            vodRemarks = "20260712下",
            episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260712%2F11873_ep4%2Findex.m3u8",
        )
        assertEquals(false, DownloadNameResolver.historyEntryMatchesDownload(episodeThree, downloadUrl))
        assertEquals(true, DownloadNameResolver.historyEntryMatchesDownload(episodeFour, downloadUrl))
    }

    @Test
    fun resolvesEpisodeFourFromHistoryWhenEpisodeThreeAlsoPresent() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        val history = listOf(
            DownloadNameResolver.FongMiHistoryEntry(
                vodName = "脱口秀和 Ta 的朋友们第三季",
                vodRemarks = "第3集",
                episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260712%2F11873_ep3%2Findex.m3u8",
            ),
            DownloadNameResolver.FongMiHistoryEntry(
                vodName = "脱口秀和 Ta 的朋友们第三季",
                vodRemarks = "第4集",
                episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260712%2F11873_ep4%2Findex.m3u8",
            ),
        )
        val title = DownloadNameResolver.resolveTitleFromHistory(history, downloadUrl)
        assertEquals("脱口秀和 Ta 的朋友们第三季-第4集", title)
    }

    @Test
    fun historyEntryMatchesEpisodeNumberInFolderStem() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        val entry = DownloadNameResolver.FongMiHistoryEntry(
            vodName = "脱口秀和 Ta 的朋友们第三季",
            vodRemarks = "第4集",
            episodeUrl = "",
        )
        assertEquals(true, DownloadNameResolver.historyEntryMatchesDownload(entry, downloadUrl))
    }

    @Test
    fun composeFongMiTitleWithoutTrailingDate() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        val title = DownloadNameResolver.composeFongMiTitle(
            DownloadNameResolver.FongMiMedia(
                title = "脱口秀和 Ta 的朋友们第三季",
                artist = "20260712下",
            ),
            downloadUrl,
        )
        assertEquals("脱口秀和 Ta 的朋友们第三季-20260712下", title)
    }

    @Test
    fun polishDisplayTitleRemovesPlayingStatusAndTrailingDate() {
        assertEquals(
            "剧名-第4集",
            DownloadNameResolver.polishDisplayTitle("正在播放·剧名-第4集-20260712"),
        )
        assertEquals(
            "21世纪大君夫人-第03集",
            DownloadNameResolver.polishDisplayTitle("21世纪大君夫人-正在播放：第03集"),
        )
    }

    @Test
    fun rejectsEpisodeThreeTitleForEpisodeFourDownload() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        assertEquals(
            false,
            DownloadNameResolver.titleMatchesDownload("脱口秀和 Ta 的朋友们第三季-第3集", downloadUrl),
        )
        assertEquals(
            true,
            DownloadNameResolver.titleMatchesDownload("脱口秀和 Ta 的朋友们第三季-第4集", downloadUrl),
        )
    }

    @Test
    fun inferEpisodeLabelFromDownloadUsesEpisodeNumberInFolderStem() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        assertEquals("第4集", DownloadNameResolver.inferEpisodeLabelFromDownload(downloadUrl))
    }

    @Test
    fun inferEpisodeLabelFromDownloadFallsBackToDate() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260411%252F4765_3011ac29%252Findex.m3u8"
        assertEquals("20260411", DownloadNameResolver.inferEpisodeLabelFromDownload(downloadUrl))
    }

    @Test
    fun composeFongMiTitleWithShowFallbackUsesDownloadEpisodeNotPlayingEpisode() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F11873_ep4%252Findex.m3u8"
        val title = DownloadNameResolver.composeFongMiTitleWithShowFallback(
            DownloadNameResolver.FongMiMedia(
                title = "21世纪大君夫人",
                artist = "正在播放：第03集",
            ),
            downloadUrl,
        )
        assertEquals("21世纪大君夫人-第4集", title)
    }

    @Test
    fun resolveShowNameFromHistoryUsesSeriesStemAndDownloadEpisode() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F4765_ep4%252Findex.m3u8"
        val history = listOf(
            DownloadNameResolver.FongMiHistoryEntry(
                vodName = "21世纪大君夫人",
                vodRemarks = "第03集",
                episodeUrl = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fv.lzcdn28.com%2F20260712%2F4765_ep3%2Findex.m3u8",
            ),
        )
        val title = DownloadNameResolver.resolveShowNameFromHistory(history, downloadUrl)
        assertEquals("21世纪大君夫人-第4集", title)
    }

    @Test
    fun extractShowNameFromTitleStripsEpisodeSuffix() {
        assertEquals(
            "21世纪大君夫人",
            DownloadNameResolver.extractShowNameFromTitle("21世纪大君夫人-第03集"),
        )
    }

    @Test
    fun seriesStemPrefixUsesUnderscoreSegment() {
        assertEquals("4765", DownloadNameResolver.seriesStemPrefix("4765_3011ac29"))
    }

    @Test
    fun mediaMatchesDownloadByFolderStemInPlayerUrl() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260411%252F4765_3011ac29%252Findex.m3u8"
        val media = DownloadNameResolver.FongMiMedia(
            title = "21世纪大君夫人",
            artist = "第04集",
            url = "https://v.lzcdn28.com/20260411/4765_3011ac29/index.m3u8",
        )
        assertEquals(true, DownloadNameResolver.mediaMatchesDownload(media, downloadUrl))
        assertEquals(
            "21世纪大君夫人-第04集",
            DownloadNameResolver.composeFongMiTitle(media, downloadUrl),
        )
    }

    @Test
    fun historyMatchesEpisodeWhenEpisodeUrlContainsFolderStem() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260411%252F4765_3011ac29%252Findex.m3u8"
        val entry = DownloadNameResolver.FongMiHistoryEntry(
            vodName = "21世纪大君夫人",
            vodRemarks = "第04集",
            episodeUrl = "https://v.lzcdn28.com/20260411/4765_3011ac29/index.m3u8",
        )
        assertEquals(true, DownloadNameResolver.historyEntryMatchesDownload(entry, downloadUrl))
        assertEquals(
            "第04集",
            DownloadNameResolver.resolveEpisodeLabelFromHistory(listOf(entry), downloadUrl),
        )
        assertEquals(
            "21世纪大君夫人-第04集",
            DownloadNameResolver.resolveTitleFromHistory(listOf(entry), downloadUrl),
        )
    }

    @Test
    fun composeFongMiTitleKeepsEpisodeForHashFolderStem() {
        val downloadUrl =
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260411%252F4765_3011ac29%252Findex.m3u8"
        val title = DownloadNameResolver.composeFongMiTitle(
            DownloadNameResolver.FongMiMedia(
                title = "21世纪大君夫人",
                artist = "正在播放：第04集",
            ),
            downloadUrl,
        )
        assertEquals("21世纪大君夫人-第04集", title)
    }
}
