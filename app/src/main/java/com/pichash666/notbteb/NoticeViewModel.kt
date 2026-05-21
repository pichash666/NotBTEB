package com.pichash666.notbteb

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NoticeViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var selectedTabIndex by mutableStateOf(0)
        private set
    
    var selectedCategory by mutableStateOf(prefs.getString("selected_category", "All") ?: "All")
        private set

    private val _notices = mutableStateListOf<Notice>()
    val notices: List<Notice> get() = _notices

    private val _results = mutableStateListOf<Notice>()
    val results: List<Notice> get() = _results

    var lastUpdate by mutableStateOf(prefs.getString("last_update", "") ?: "")
        private set

    var lastResultUpdate by mutableStateOf(prefs.getString("special_last_update", "") ?: "")
        private set

    private val _lastSyncTime = MutableStateFlow(prefs.getLong("last_background_check_time", 0L))
    val lastSyncTime: StateFlow<Long> = _lastSyncTime.asStateFlow()

    var isLoading by mutableStateOf(false)
        private set

    var isIgnoringBatteryOptimizations by mutableStateOf(false)
        private set

    private val preferenceListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
        when (key) {
            "last_background_check_time" -> _lastSyncTime.value = p.getLong(key, 0L)
            "last_update" -> lastUpdate = p.getString(key, "") ?: ""
            "special_last_update" -> lastResultUpdate = p.getString(key, "") ?: ""
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        checkBatteryOptimizations()
        refreshData()
    }

    fun checkBatteryOptimizations() {
        val pm = getApplication<Application>().getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(getApplication<Application>().packageName)
    }

    override fun onCleared() {
        super.onCleared()
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
    }

    fun setTabIndex(index: Int) {
        selectedTabIndex = index
        refreshData()
    }

    fun setCategory(category: String) {
        selectedCategory = category
        prefs.edit().putString("selected_category", category).apply()
        if (selectedTabIndex == 0) {
            refreshData()
        }
    }

    fun refreshData() {
        if (isLoading) return
        viewModelScope.launch {
            isLoading = true
            try {
                if (selectedTabIndex == 0) {
                    val response = NoticeScraper.fetchNotices(selectedCategory)
                    _notices.clear()
                    _notices.addAll(response.notices)
                    if (response.lastUpdate.isNotEmpty()) {
                        lastUpdate = response.lastUpdate
                        prefs.edit().putString("last_update", lastUpdate).apply()
                    }
                } else {
                    val response = NoticeScraper.fetchResults()
                    _results.clear()
                    _results.addAll(response.notices)
                    if (response.lastUpdate.isNotEmpty()) {
                        lastResultUpdate = response.lastUpdate
                        prefs.edit().putString("special_last_update", lastResultUpdate).apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}
