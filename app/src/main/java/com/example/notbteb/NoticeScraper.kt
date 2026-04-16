package com.example.notbteb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

data class Notice(
    val title: String,
    val date: String,
    val link: String
)

object NoticeScraper {
    private const val BASE_URL = "https://bteb.gov.bd"
    
    val URL_MAP = mapOf(
        "All" to "$BASE_URL/pages/notices",
        "Diploma" to "$BASE_URL/pages/static-pages/691997ba933eb65569dde8a7",
        "SSC" to "$BASE_URL/pages/static-pages/691997bf933eb65569ddebf6",
        "HSC" to "$BASE_URL/pages/static-pages/691997b6933eb65569dde56c"
    )

    suspend fun fetchNotices(category: String): List<Notice> = withContext(Dispatchers.IO) {
        val url = URL_MAP[category] ?: URL_MAP["All"]!!
        val notices = mutableListOf<Notice>()
        
        try {
            val doc = Jsoup.connect(url).get()
            
            if (category == "All") {
                val rows = doc.select("table tbody tr")
                for (row in rows) {
                    val cols = row.select("td")
                    if (cols.size >= 5) {
                        val title = cols[1].text().trim()
                        val date = cols[4].text().trim()
                        val linkElement = cols.last()?.select("a")?.first()
                        var link = linkElement?.attr("href") ?: ""
                        if (link.startsWith("/")) link = BASE_URL + link
                        if (title.isNotEmpty()) notices.add(Notice(title, date, link))
                    }
                }
            } else {
                // Static pages (Diploma, SSC, HSC)
                // Use a more broad selection and then filter by common notice patterns
                val items = doc.select("li, tr")
                
                for (item in items) {
                    val linkElement = item.select("a").first() ?: continue
                    val fullText = item.text().trim()
                    
                    // Pattern for date: YYYY-MM-DD or DD-MM-YYYY or DD/MM/YYYY
                    val dateRegex = """(\d{4}-\d{2}-\d{2}|\d{2}-\d{2}-\d{4}|\d{2}/\d{2}/\d{4})""".toRegex()
                    val dateMatch = dateRegex.find(fullText)
                    
                    if (dateMatch != null) {
                        val date = dateMatch.value
                        val title = fullText.replace(date, "").trim()
                        
                        // Clean up title: remove common prefixes/suffixes and brackets
                        val cleanTitle = title.replace(Regex("""^[\[\]\s:-]+"""), "")
                                             .replace(Regex("""[\[\]\s:-]+$"""), "")
                        
                        val link = linkElement.attr("href").let { 
                            if (it.startsWith("/")) BASE_URL + it else it 
                        }
                        
                        // Avoid adding duplicates and very short titles (unlikely to be notices)
                        if (cleanTitle.length > 5 && !notices.any { it.title == cleanTitle }) {
                            notices.add(Notice(cleanTitle, date, link))
                        }
                    }
                }
                
                // Sort by date descending if possible (assuming YYYY-MM-DD format mostly)
                notices.sortByDescending { it.date }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        notices
    }
}
