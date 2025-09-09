package com.downloadmanager.app

import com.downloadmanager.app.model.DownloadFile
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class FileFetcher(
    private val userAgent: String,
    private val jsoupTimeoutMs: Int,
    private val getSubfolderName: () -> String,
    private val getFileType: (String) -> String,
    private val getFileSize: (String) -> String,
) {
    fun fetchFromDirectory(url: String): List<DownloadFile> {
        val files = mutableListOf<DownloadFile>()
        val subfolder = getSubfolderName()
        val doc = fetchDocument(url)
        doc.select("a[href]")
            .asSequence()
            .map { it to it.attr("href") }
            .filter { (_, href) -> href != "../" }
            .map { (el, _) -> absoluteUrl(url, el) }
            .filter { it.isNotBlank() }
            .filter { isValidFileUrl(it) }
            .forEach { fullUrl ->
                val fileName = decodeFileName(URI(fullUrl).path.substringAfterLast('/'))
                val fileType = getFileType(fullUrl)
                val fileSize = getFileSize(fullUrl)
                files.add(DownloadFile(fileName, fullUrl, fileSize, fileType, subfolder))
            }
        return files
    }

    fun fetchFromHtml(url: String): List<DownloadFile> {
        val files = mutableListOf<DownloadFile>()
        val subfolder = getSubfolderName()
        val doc = fetchDocument(url)
        val selectors = listOf(
            "a[href*='.mp4']", "a[href*='.mkv']", "a[href*='.avi']", "a[href*='.mov']",
            "a[href*='.mp3']", "a[href*='.wav']", "a[href*='.flac']", "a[href*='.aac']",
            "a[href*='.pdf']", "a[href*='.zip']", "a[href*='.rar']"
        )
        selectors.asSequence()
            .flatMap { sel -> doc.select(sel).asSequence() }
            .map { el -> absoluteUrl(url, el) }
            .filter { it.isNotBlank() }
            .filter { isValidFileUrl(it) }
            .forEach { fullUrl ->
                val fileName = decodeFileName(URI(fullUrl).path.substringAfterLast('/'))
                val fileType = getFileType(fullUrl)
                val fileSize = getFileSize(fullUrl)
                files.add(DownloadFile(fileName, fullUrl, fileSize, fileType, subfolder))
            }
        return files
    }

    private fun isValidFileUrl(href: String): Boolean {
        val path = try { URI(href).path ?: href } catch (_: Exception) { href }
        val lower = path.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
            lower.endsWith(".mov") || lower.endsWith(".mp3") || lower.endsWith(".wav") ||
            lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".pdf") ||
            lower.endsWith(".zip") || lower.endsWith(".rar")
    }

    private fun absoluteUrl(baseUrl: String, element: Element): String {
        val abs = element.attr("abs:href")
        val href = element.attr("href").trim()
        val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val result = if (abs.isNotBlank()) {
            abs
        } else if (href.isBlank()) {
            ""
        } else {
            resolveUrlSafe(normalizedBase, href)
        }
        return result
    }

    private fun resolveUrlSafe(base: String, href: String): String {
        return try {
            URI(base).resolve(href).toString()
        } catch (_: Exception) {
            if (href.startsWith("http")) href else base + href
        }
    }

    private fun decodeFileName(name: String): String {
        return try {
            URLDecoder.decode(name, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            name
        }
    }

    private fun fetchDocument(url: String): org.jsoup.nodes.Document {
        // Use HttpURLConnection to avoid double-encoding of already-encoded `%` sequences
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = jsoupTimeoutMs
            readTimeout = jsoupTimeoutMs
            setRequestProperty("User-Agent", userAgent)
        }
        connection.inputStream.use { stream ->
            return Jsoup.parse(stream, StandardCharsets.UTF_8.name(), url)
        }
    }
}
