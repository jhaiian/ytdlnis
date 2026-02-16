package com.deniscerri.ytdl.ui.more.settings

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager

data class PreferenceSearchData(
    val key: String,
    val title: String,
    val summary: String,
    val categoryKey: String,
    val categoryTitle: String,
    val xmlRes: Int,
    val isParent: Boolean,
    val depth: Int,
    val parentKey: String?,
    val originalPreference: Preference
)

data class SearchMatch(
    val data: PreferenceSearchData,
    val score: Float,
    val matchedFields: Set<MatchField>
)

enum class MatchField(val weight: Float) {
    TITLE_EXACT(10f),
    TITLE_START(8f),
    TITLE_CONTAINS(5f),
    KEY_EXACT(9f),
    KEY_CONTAINS(6f),
    SUMMARY_EXACT(7f),
    SUMMARY_CONTAINS(4f),
    CATEGORY_MATCH(3f),
    FUZZY_MATCH(2f)
}

class SettingsSearchManager(
    private val fragment: MainSettingsFragment,
    private val categoryFragmentMap: Map<String, Int>,
    private val categoryTitles: Map<String, Int>
) {
    
    private var preferenceCache: List<PreferenceSearchData>? = null
    private var cacheInitialized = false
    
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    private val DEBOUNCE_DELAY_MS = 300L
    
    fun initializeCache() {
        if (cacheInitialized) return
        
        Thread {
            try {
                val allPreferences = mutableListOf<PreferenceSearchData>()
                
                categoryFragmentMap.forEach { (categoryKey, xmlRes) ->
                    val categoryTitle = fragment.getString(
                        categoryTitles[categoryKey] ?: com.deniscerri.ytdl.R.string.settings
                    )
                    
                    try {
                        val preferenceManager = PreferenceManager(fragment.requireContext())
                        val tempScreen = preferenceManager.inflateFromResource(
                            fragment.requireContext(), 
                            xmlRes, 
                            null
                        )
                        
                        collectPreferencesForCache(
                            tempScreen, 
                            categoryKey, 
                            categoryTitle, 
                            xmlRes,
                            allPreferences,
                            null,
                            0
                        )
                    } catch (e: Exception) {
                        Log.e("SearchManager", "Error caching preferences from $categoryKey", e)
                    }
                }
                
                preferenceCache = allPreferences
                cacheInitialized = true
                Log.d("SearchManager", "Cache initialized with ${allPreferences.size} preferences")
            } catch (e: Exception) {
                Log.e("SearchManager", "Error initializing preference cache", e)
            }
        }.start()
    }
    
    private fun collectPreferencesForCache(
        group: PreferenceGroup,
        categoryKey: String,
        categoryTitle: String,
        xmlRes: Int,
        results: MutableList<PreferenceSearchData>,
        parentKey: String?,
        depth: Int
    ) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)
            
            val data = PreferenceSearchData(
                key = pref.key ?: "",
                title = pref.title?.toString() ?: "",
                summary = pref.summary?.toString() ?: "",
                categoryKey = categoryKey,
                categoryTitle = categoryTitle,
                xmlRes = xmlRes,
                isParent = pref is PreferenceGroup,
                depth = depth,
                parentKey = parentKey,
                originalPreference = pref
            )
            
            results.add(data)
            
            if (pref is PreferenceGroup) {
                collectPreferencesForCache(
                    pref, 
                    categoryKey, 
                    categoryTitle, 
                    xmlRes,
                    results,
                    pref.key,
                    depth + 1
                )
            }
        }
    }
    
    fun searchWithDebounce(query: String, callback: (List<SearchMatch>) -> Unit) {
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        
        searchRunnable = Runnable {
            performSearch(query, callback)
        }
        searchHandler.postDelayed(searchRunnable!!, DEBOUNCE_DELAY_MS)
    }
    
    fun performSearch(query: String, callback: (List<SearchMatch>) -> Unit) {
        if (query.isBlank()) {
            callback(emptyList())
            return
        }
        
        Thread {
            try {
                val results = searchPreferences(query)
                fragment.requireActivity().runOnUiThread {
                    callback(results)
                }
            } catch (e: Exception) {
                Log.e("SearchManager", "Error performing search", e)
                fragment.requireActivity().runOnUiThread {
                    callback(emptyList())
                }
            }
        }.start()
    }
    
    private fun searchPreferences(query: String): List<SearchMatch> {
        val queryLower = query.lowercase().trim()
        val cache = preferenceCache ?: return emptyList()
        
        val matches = mutableListOf<SearchMatch>()
        
        cache.forEach { data ->
            val matchedFields = mutableSetOf<MatchField>()
            var score = 0f
            
            if (data.key.isBlank() && data.title.isBlank()) {
                return@forEach
            }
            
            val titleLower = data.title.lowercase()
            val summaryLower = data.summary.lowercase()
            val keyLower = data.key.lowercase()
            val categoryLower = data.categoryTitle.lowercase()
            
            when {
                titleLower == queryLower -> {
                    matchedFields.add(MatchField.TITLE_EXACT)
                    score += MatchField.TITLE_EXACT.weight
                }
                titleLower.startsWith(queryLower) -> {
                    matchedFields.add(MatchField.TITLE_START)
                    score += MatchField.TITLE_START.weight
                }
                titleLower.contains(queryLower) -> {
                    matchedFields.add(MatchField.TITLE_CONTAINS)
                    score += MatchField.TITLE_CONTAINS.weight
                }
            }
            
            when {
                keyLower == queryLower -> {
                    matchedFields.add(MatchField.KEY_EXACT)
                    score += MatchField.KEY_EXACT.weight
                }
                keyLower.contains(queryLower) -> {
                    matchedFields.add(MatchField.KEY_CONTAINS)
                    score += MatchField.KEY_CONTAINS.weight
                }
            }
            
            when {
                summaryLower == queryLower -> {
                    matchedFields.add(MatchField.SUMMARY_EXACT)
                    score += MatchField.SUMMARY_EXACT.weight
                }
                summaryLower.contains(queryLower) -> {
                    matchedFields.add(MatchField.SUMMARY_CONTAINS)
                    score += MatchField.SUMMARY_CONTAINS.weight
                }
            }
            
            if (categoryLower.contains(queryLower)) {
                matchedFields.add(MatchField.CATEGORY_MATCH)
                score += MatchField.CATEGORY_MATCH.weight
            }
            
            if (matchedFields.isEmpty() && query.length >= 3) {
                val fuzzyScore = calculateFuzzyScore(titleLower, queryLower)
                if (fuzzyScore > 0.6f) {
                    matchedFields.add(MatchField.FUZZY_MATCH)
                    score += MatchField.FUZZY_MATCH.weight * fuzzyScore
                }
            }
            
            if (matchedFields.isNotEmpty() && data.title.length < 50) {
                score *= (1f + (50f - data.title.length) / 100f)
            }
            
            if (data.depth == 0 && matchedFields.isNotEmpty()) {
                score *= 1.2f
            }
            
            if (matchedFields.isNotEmpty()) {
                matches.add(SearchMatch(data, score, matchedFields))
            }
        }
        
        return matches.sortedByDescending { it.score }
    }
    
    private fun calculateFuzzyScore(target: String, query: String): Float {
        if (target.isEmpty() || query.isEmpty()) return 0f
        
        val distance = levenshteinDistance(target, query)
        val maxLength = maxOf(target.length, query.length)
        
        return 1f - (distance.toFloat() / maxLength)
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        
        if (len1 == 0) return len2
        if (len2 == 0) return len1
        
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        
        return dp[len1][len2]
    }
    
    fun clearCache() {
        preferenceCache = null
        cacheInitialized = false
        searchRunnable?.let { searchHandler.removeCallbacks(it) }
        searchRunnable = null
    }
}
