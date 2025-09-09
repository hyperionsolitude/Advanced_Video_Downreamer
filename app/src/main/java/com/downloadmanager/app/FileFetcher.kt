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
    companion object {
        private const val MIN_FILENAME_LENGTH = 5
        private const val MIN_SHORT_FILENAME_LENGTH = 3
    }
    fun fetchFromDirectory(url: String): List<DownloadFile> {
        val files = mutableListOf<DownloadFile>()
        val subfolder = getSubfolderName()
        val doc = fetchDocument(url)
        doc.select("a[href]")
            .asSequence()
            .map { it to it.attr("href") }
            .filter { (_, href) -> href != "../" }
            .map { (el, href) -> el to absoluteUrl(url, el) }
            .filter { (_, fullUrl) -> fullUrl.isNotBlank() }
            .filter { (_, fullUrl) -> isValidFileUrl(fullUrl) }
            .forEach { (element, fullUrl) ->
                val fileName = extractBestFileName(element, fullUrl)
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
            .map { el -> el to absoluteUrl(url, el) }
            .filter { (_, fullUrl) -> fullUrl.isNotBlank() }
            .filter { (_, fullUrl) -> isValidFileUrl(fullUrl) }
            .forEach { (element, fullUrl) ->
                val fileName = extractBestFileName(element, fullUrl)
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

    private fun extractBestFileName(element: Element, fullUrl: String): String {
        // First try to get the link text (display text)
        val linkText = element.text().trim()

        // Get filename from URL path
        val urlFileName = try {
            decodeFileName(URI(fullUrl).path.substringAfterLast('/'))
        } catch (_: Exception) {
            fullUrl.substringAfterLast('/')
        }

        // Choose the best filename based on quality indicators
        return when {
            isLinkTextValid(linkText) -> cleanFileName(linkText)
            isUrlFileNameValid(urlFileName) -> urlFileName
            linkText.isNotBlank() -> cleanFileName(linkText)
            else -> urlFileName
        }
    }

    private fun isLinkTextValid(linkText: String): Boolean {
        return linkText.isNotBlank() &&
            linkText.contains('.') &&
            linkText.length > MIN_FILENAME_LENGTH &&
            !linkText.matches(Regex("^\\d+\\s*\\[.*\\]$"))
    }

    private fun isUrlFileNameValid(urlFileName: String): Boolean {
        return urlFileName.isNotBlank() &&
            !urlFileName.matches(Regex("^\\d+\\s*\\[.*\\]$")) &&
            urlFileName.length > MIN_SHORT_FILENAME_LENGTH
    }

    private fun cleanFileName(name: String): String {
        return name
            .replace(Regex("\\s+"), " ") // Replace multiple spaces with single space
            .replace(Regex("[<>:\"|?*]"), "_") // Replace invalid filename characters
            .trim()
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
