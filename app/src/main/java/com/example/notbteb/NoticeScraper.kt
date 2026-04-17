package com.example.notbteb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class Notice(
    val title: String,
    val date: String,
    val link: String
)

data class NoticeResponse(
    val notices: List<Notice>,
    val lastUpdate: String
)

object NoticeScraper {
    private const val BASE_URL = "https://bteb.gov.bd"
    const val SPECIAL_NOTICE_URL = "$BASE_URL/pages/static-pages/691997b2933eb65569dde217"
    
    val URL_MAP = mapOf(
        "All" to "$BASE_URL/pages/notices",
        "Diploma" to "$BASE_URL/pages/static-pages/691997ba933eb65569dde8a7",
        "SSC" to "$BASE_URL/pages/static-pages/691997bf933eb65569ddebf6",
        "HSC" to "$BASE_URL/pages/static-pages/691997b6933eb65569dde56c"
    )

    private val dateRegex = """(\d{4}-\d{2}-\d{2}|\d{2}-\d{2}-\d{4}|\d{2}/\d{2}/\d{4})""".toRegex()
    private val updateRegex = """(?:হাল-নাগাদ করা হয়েছে|Last Updated)[:\s]+([^এ\n]+)""".toRegex()
    private val titlePrefixRegex = """^[\[\]\s:-]+""".toRegex()
    private val titleSuffixRegex = """[\[\]\s:-]+$""".toRegex()
    
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    suspend fun fetchNotices(category: String): NoticeResponse = fetchFromUrl(URL_MAP[category] ?: URL_MAP["All"]!!, category == "All", isResult = false)

    suspend fun fetchResults(): NoticeResponse = fetchFromUrl(SPECIAL_NOTICE_URL, false, isResult = true)

    suspend fun fetchSpecialUpdateDate(): String = withContext(Dispatchers.IO) {
        try {
            val doc = Jsoup.connect(SPECIAL_NOTICE_URL)
                .timeout(15000)
                .userAgent(USER_AGENT)
                .get()
            val footerText = doc.text()
            updateRegex.find(footerText)?.groupValues?.get(1)?.trim() ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private suspend fun fetchFromUrl(url: String, isTable: Boolean, isResult: Boolean): NoticeResponse = withContext(Dispatchers.IO) {
        val notices = mutableListOf<Notice>()
        var siteLastUpdate = ""
        
        try {
            val doc = Jsoup.connect(url)
                .timeout(20000)
                .userAgent(USER_AGENT)
                .get()
            
            val footerText = doc.text()
            siteLastUpdate = updateRegex.find(footerText)?.groupValues?.get(1)?.trim() ?: ""

            // Target the main content area of the Bangladesh National Portal
            val mainContent = doc.select("#printable_area, .column.eight.units, .content-details, .p-content").first() ?: doc

            if (isResult) {
                // First try to find a table inside a figure (common in newer portal updates)
                val table = mainContent.select("figure.table table").first()
                if (table != null) {
                    val rows = table.select("tbody tr")
                    for (row in rows) {
                        val cols = row.select("td")
                        if (cols.isNotEmpty()) {
                            val titleElement = cols.select("a").first()
                            val title = titleElement?.text()?.trim() ?: row.text().trim()
                            var link = titleElement?.attr("href") ?: ""
                            
                            if (title.length > 5) {
                                if (link.startsWith("/")) link = BASE_URL + link
                                val date = dateRegex.find(title)?.value ?: ""
                                if (link.isNotEmpty() && !notices.any { it.link == link }) {
                                    notices.add(Notice(title, date, link))
                                }
                            }
                        }
                    }
                }

                // If no table found or empty, fallback to the link list logic
                if (notices.isEmpty()) {
                    val links = mainContent.select("a")
                    for (linkElement in links) {
                        val title = linkElement.text().trim()
                        var link = linkElement.attr("href")
                        
                        // Results typically have "Result" or "ফলাফল" or a date in them
                        if (title.length > 10 && (title.contains("Result", true) || title.contains("ফলাফল", true) || dateRegex.containsMatchIn(title))) {
                            if (link.startsWith("/")) link = BASE_URL + link
                            val date = dateRegex.find(title)?.value ?: ""
                            val cleanTitle = title.replace(date, "").replace(titlePrefixRegex, "").replace(titleSuffixRegex, "").trim()
                            
                            if (cleanTitle.isNotEmpty() && !notices.any { it.link == link }) {
                                notices.add(Notice(cleanTitle, date, link))
                            }
                        }
                    }
                }
            } else if (isTable) {
                val rows = mainContent.select("table tbody tr")
                for (row in rows) {
                    val cols = row.select("td")
                    if (cols.size >= 5) {
                        val title = cols[1].text().trim()
                        val date = cols[4].text().trim()
                        val linkElement = cols.select("a").first() ?: row.select("a").first()
                        var link = linkElement?.attr("href") ?: ""
                        if (link.startsWith("/")) link = BASE_URL + link
                        if (title.isNotEmpty()) notices.add(Notice(title, date, link))
                    }
                }
            } else {
                val items = mainContent.select("li, tr, p:has(a)")
                for (item in items) {
                    val linkElement = item.select("a").first() ?: continue
                    val fullText = item.text().trim()
                    val dateMatch = dateRegex.find(fullText)
                    
                    if (dateMatch != null) {
                        val date = dateMatch.value
                        val title = fullText.replace(date, "").trim()
                        val cleanTitle = title.replace(titlePrefixRegex, "").replace(titleSuffixRegex, "").trim()
                        var link = linkElement.attr("href")
                        if (link.startsWith("/")) link = BASE_URL + link
                        
                        if (cleanTitle.length > 5 && !notices.any { it.link == link }) {
                            notices.add(Notice(cleanTitle, date, link))
                        }
                    }
                }
            }
            
            // Post-process: Sort by date if possible (though site usually is sorted)
            notices.sortByDescending { it.date }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        val displayUpdate = notices.firstOrNull()?.date ?: siteLastUpdate
        NoticeResponse(notices.distinctBy { it.link }, displayUpdate)
    }
}
