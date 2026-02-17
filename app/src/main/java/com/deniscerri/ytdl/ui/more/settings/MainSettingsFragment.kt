package com.deniscerri.ytdl.ui.more.settings

import android.annotation.SuppressLint
import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.LayoutDirection
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.text.layoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.deniscerri.ytdl.BuildConfig
import com.deniscerri.ytdl.R
import com.deniscerri.ytdl.database.models.BackupSettingsItem
import com.deniscerri.ytdl.database.models.CommandTemplate
import com.deniscerri.ytdl.database.models.CookieItem
import com.deniscerri.ytdl.database.models.DownloadItem
import com.deniscerri.ytdl.database.models.HistoryItem
import com.deniscerri.ytdl.database.models.RestoreAppDataItem
import com.deniscerri.ytdl.database.models.observeSources.ObserveSourcesItem
import com.deniscerri.ytdl.database.models.SearchHistoryItem
import com.deniscerri.ytdl.database.models.TemplateShortcut
import com.deniscerri.ytdl.database.viewmodel.SettingsViewModel
import com.deniscerri.ytdl.util.FileUtil
import com.deniscerri.ytdl.util.ThemeUtil
import com.deniscerri.ytdl.util.UiUtil
import com.deniscerri.ytdl.util.UpdateUtil
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainSettingsFragment : BaseSettingsFragment() {
    override val title: Int = R.string.settings

    private var appearance: Preference? = null
    private var folders: Preference? = null
    private var downloading: Preference? = null
    private var processing: Preference? = null
    private var updating: Preference? = null
    private var advanced: Preference? = null

    private var backup : Preference? = null
    private var restore : Preference? = null
    private var backupPath : Preference? = null

    private var updateUtil: UpdateUtil? = null
    private var activeDownloadCount = 0

    private lateinit var settingsViewModel: SettingsViewModel
    private lateinit var editor: SharedPreferences.Editor
    private lateinit var searchManager: SettingsSearchManager

    private val searchCategories = mutableMapOf<String, PreferenceCategory>()
    private var isSearchMode = false
    private var lastSearchQuery: String = ""

    private val categoryFragmentMap = mapOf(
        "appearance" to R.xml.general_preferences,
        "folders" to R.xml.folders_preference,
        "downloading" to R.xml.downloading_preferences,
        "processing" to R.xml.processing_preferences,
        "updating" to R.xml.updating_preferences,
        "advanced" to R.xml.advanced_preferences
    )

    private val categoryTitles = mapOf(
        "appearance" to R.string.general,
        "folders" to R.string.directories,
        "downloading" to R.string.downloads,
        "processing" to R.string.processing,
        "updating" to R.string.updating,
        "advanced" to R.string.advanced
    )
    
    private val categoryNavigationActions = mapOf(
        "appearance" to R.id.action_mainSettingsFragment_to_appearanceSettingsFragment,
        "folders" to R.id.action_mainSettingsFragment_to_folderSettingsFragment,
        "downloading" to R.id.action_mainSettingsFragment_to_downloadSettingsFragment,
        "processing" to R.id.action_mainSettingsFragment_to_processingSettingsFragment,
        "updating" to R.id.action_mainSettingsFragment_to_updateSettingsFragment,
        "advanced" to R.id.action_mainSettingsFragment_to_advancedSettingsFragment
    )

    private data class HierarchicalPreference(
        val preference: Preference,
        val parent: PreferenceGroup?,
        val depth: Int = 0,
        val isParent: Boolean = false
    )

    companion object {
        const val ARG_HIGHLIGHT_KEY = "highlight_preference_key"
        const val ARG_RETURN_TO_SEARCH = "return_to_search"
    }
    
    fun handleBackPressed(): Boolean {
        if (isSearchMode) {
            restoreNormalView()
            return true
        }
        return false
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        buildPreferenceList(preferenceScreen)

        val navController = findNavController()
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        editor = preferences.edit()

        appearance = findPreference("appearance")
        folders = findPreference("folders")
        downloading = findPreference("downloading")
        processing = findPreference("processing")
        updating = findPreference("updating")
        advanced = findPreference("advanced")

        val separator = if (Locale(preferences.getString("app_language", "en")!!).layoutDirection == LayoutDirection.RTL) "،" else ","
        appearance?.summary = "${if (Build.VERSION.SDK_INT < 33) getString(R.string.language) + "$separator " else ""}${getString(R.string.Theme)}$separator ${getString(R.string.accents)}$separator ${getString(R.string.preferred_search_engine)}"
        appearance?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_appearanceSettingsFragment)
            true
        }

        folders?.summary = "${getString(R.string.music_directory)}$separator ${getString(R.string.video_directory)}$separator ${getString(R.string.command_directory)}"
        folders?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_folderSettingsFragment)
            true
        }

        downloading?.summary = "${getString(R.string.quick_download)}$separator ${getString(R.string.concurrent_downloads)}$separator ${getString(R.string.limit_rate)}"
        downloading?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_downloadSettingsFragment)
            true
        }

        processing?.summary = "${getString(R.string.sponsorblock)}$separator ${getString(R.string.embed_subtitles)}$separator ${getString(R.string.add_chapters)}"
        processing?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_processingSettingsFragment)
            true
        }

        updating?.summary = "${getString(R.string.update_ytdl)}$separator ${getString(R.string.update_app)}"
        updating?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_updateSettingsFragment)
            true
        }

        advanced?.summary = "PO Token$separator ${getString(R.string.other_youtube_extractor_args)}"
        advanced?.setOnPreferenceClickListener {
            navController.navigate(R.id.action_mainSettingsFragment_to_advancedSettingsFragment)
            true
        }

        updateUtil = UpdateUtil(requireContext())

        WorkManager.getInstance(requireContext()).getWorkInfosByTagLiveData("download").observe(this){
            activeDownloadCount = 0
            it.forEach {w ->
                if (w.state == WorkInfo.State.RUNNING) activeDownloadCount++
            }
        }
        settingsViewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        
        searchManager = SettingsSearchManager(this, categoryFragmentMap, categoryTitles)
        searchManager.initializeCache()

        backup = findPreference("backup")
        restore = findPreference("restore")
        backupPath = findPreference("backup_path")

        findPreference<Preference>("package_name")?.apply {
            summary = BuildConfig.APPLICATION_ID
            setOnPreferenceClickListener {
                UiUtil.copyToClipboard(summary.toString(), requireActivity())
                true
            }
        }

        backup!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val builder = MaterialAlertDialogBuilder(requireContext())
                builder.setTitle(getString(R.string.select_backup_categories))
                val values = resources.getStringArray(R.array.backup_category_values)
                val entries = resources.getStringArray(R.array.backup_category_entries)
                val checkedItems : ArrayList<Boolean> = arrayListOf()
                values.forEach { _ ->
                    checkedItems.add(true)
                }

                builder.setMultiChoiceItems(
                    entries,
                    checkedItems.toBooleanArray()
                ) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }

                builder.setPositiveButton(
                    getString(R.string.ok)
                ) { _: DialogInterface?, _: Int ->
                    lifecycleScope.launch {
                        if (checkedItems.all { !it }){
                            Snackbar.make(requireView(), R.string.select_backup_categories, Snackbar.LENGTH_SHORT).show()
                            return@launch
                        }

                        val selectedItems = values.mapIndexed { idx, it -> Pair<String, Boolean>(it, checkedItems[idx]) }.filter { it.second }.map { it.first }
                        val pathResult = withContext(Dispatchers.IO){
                            settingsViewModel.backup(selectedItems)
                        }

                        if (pathResult.isFailure) {
                            val errorMessage = pathResult.exceptionOrNull()?.message ?: requireContext().getString(R.string.errored)
                            val snack = Snackbar.make(requireView(), errorMessage, Snackbar.LENGTH_LONG)
                            val snackbarView: View = snack.view
                            val snackTextView = snackbarView.findViewById<View>(com.google.android.material.R.id.snackbar_text) as TextView
                            snackTextView.maxLines = 9999999
                            snack.setAction(android.R.string.copy){
                                UiUtil.copyToClipboard(errorMessage ?: requireContext().getString(R.string.errored), requireActivity())
                            }
                            snack.show()
                        }else {
                            val s = Snackbar.make(
                                requireView(),
                                getString(R.string.backup_created_successfully),
                                Snackbar.LENGTH_LONG
                            )
                            s.setAction(R.string.Open_File) {
                                FileUtil.openFileIntent(requireActivity(), pathResult.getOrNull()!!)
                            }
                            s.show()
                        }
                    }
                }

                builder.setNegativeButton(
                    getString(R.string.cancel)
                ) { _: DialogInterface?, _: Int -> }

                val dialog = builder.create()
                dialog.show()
                true
            }
        restore!!.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                appRestoreResultLauncher.launch(intent)
                true
            }

        backupPath?.apply {
            summary = FileUtil.formatPath(FileUtil.getBackupPath(requireContext()))
            setOnPreferenceClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                backupPathResultLauncher.launch(intent)
                true
            }
        }
    }

    override fun filterPreferences(query: String) {
        if (query.isEmpty()) {
            restoreNormalView()
            return
        }

        lastSearchQuery = query
        
        // Use debounced search for better performance
        searchManager.searchWithDebounce(query) { smartMatches ->
            // Combine with traditional deep search for reliability
            enterSearchModeEnhanced(query.lowercase(), smartMatches)
        }
    }
    
    private fun enterSearchModeEnhanced(query: String, smartMatches: List<SearchMatch>) {
        isSearchMode = true
        
        appearance?.isVisible = false
        folders?.isVisible = false
        downloading?.isVisible = false
        processing?.isVisible = false
        updating?.isVisible = false
        advanced?.isVisible = false
        
        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()

        // Use traditional search to ensure we find everything
        categoryFragmentMap.forEach { (categoryKey, xmlRes) ->
            val hierarchicalResults = findMatchingPreferencesWithHierarchy(xmlRes, query)
            
            if (hierarchicalResults.isNotEmpty()) {
                val mainCategory = PreferenceCategory(requireContext()).apply {
                    title = getString(categoryTitles[categoryKey] ?: R.string.settings)
                    key = "search_main_$categoryKey"
                }
                
                preferenceScreen.addPreference(mainCategory)
                
                // Sort results using smart scoring if available
                val sortedResults = sortWithSmartScoring(hierarchicalResults, smartMatches, categoryKey)
                buildHierarchicalPreferences(sortedResults, mainCategory, categoryKey, query)
                
                searchCategories[categoryKey] = mainCategory
            }
        }

        super.filterPreferences(query)
        hideEmptyCategoriesInMain()
    }
    
    private fun sortWithSmartScoring(
        results: List<HierarchicalPreference>,
        smartMatches: List<SearchMatch>,
        categoryKey: String
    ): List<HierarchicalPreference> {
        // Create a score map from smart matches
        val scoreMap = smartMatches
            .filter { it.data.categoryKey == categoryKey }
            .associateBy({ it.data.key }, { it.score })
        
        // Sort results by smart score if available, otherwise keep original order
        return results.sortedByDescending { hierPref ->
            scoreMap[hierPref.preference.key] ?: 0f
        }
    }

    private fun restoreNormalView() {
        isSearchMode = false
        lastSearchQuery = ""
        
        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()

        restoreAllPreferences()
        appearance?.isVisible = true
        folders?.isVisible = true
        downloading?.isVisible = true
        processing?.isVisible = true
        updating?.isVisible = true
        advanced?.isVisible = true
        
        findPreference<PreferenceCategory>("backup_restore")?.isVisible = true
    }

    private fun enterSearchMode(query: String) {
        isSearchMode = true
        
        appearance?.isVisible = false
        folders?.isVisible = false
        downloading?.isVisible = false
        processing?.isVisible = false
        updating?.isVisible = false
        advanced?.isVisible = false
        
        searchCategories.values.forEach { category ->
            preferenceScreen.removePreference(category)
        }
        searchCategories.clear()

        categoryFragmentMap.forEach { (categoryKey, xmlRes) ->
            val hierarchicalResults = findMatchingPreferencesWithHierarchy(xmlRes, query)
            
            if (hierarchicalResults.isNotEmpty()) {
                val mainCategory = PreferenceCategory(requireContext()).apply {
                    title = getString(categoryTitles[categoryKey] ?: R.string.settings)
                    key = "search_main_$categoryKey"
                }
                
                preferenceScreen.addPreference(mainCategory)
                
                buildHierarchicalPreferences(hierarchicalResults, mainCategory, categoryKey, query)
                
                searchCategories[categoryKey] = mainCategory
            }
        }

        super.filterPreferences(query)
        hideEmptyCategoriesInMain()
    }

    private fun findMatchingPreferencesWithHierarchy(xmlRes: Int, query: String): List<HierarchicalPreference> {
        val results = mutableListOf<HierarchicalPreference>()
        
        try {
            val preferenceManager = PreferenceManager(requireContext())
            val tempScreen = preferenceManager.inflateFromResource(requireContext(), xmlRes, null)
            collectMatchingWithHierarchy(tempScreen, query, results, null, 0)
        } catch (e: Exception) {
            Log.e("MainSettings", "Error loading preferences from XML", e)
        }
        
        return results
    }

    private fun collectMatchingWithHierarchy(
        group: PreferenceGroup,
        query: String,
        results: MutableList<HierarchicalPreference>,
        parent: PreferenceGroup?,
        depth: Int
    ) {
        for (i in 0 until group.preferenceCount) {
            val pref = group.getPreference(i)

            val title = pref.title?.toString() ?: ""
            val summary = pref.summary?.toString() ?: ""
            val key = pref.key ?: ""

            val matches = title.lowercase().contains(query) ||
                    summary.lowercase().contains(query) ||
                    key.lowercase().contains(query)

            if (pref is PreferenceGroup) {
                val childMatches = mutableListOf<HierarchicalPreference>()
                collectMatchingWithHierarchy(pref, query, childMatches, pref, depth + 1)
                
                if (childMatches.isNotEmpty()) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = true))
                    results.addAll(childMatches)
                } else if (matches) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = true))
                }
            } else {
                if (matches) {
                    results.add(HierarchicalPreference(pref, parent, depth, isParent = false))
                }
            }
        }
    }

    private fun buildHierarchicalPreferences(
        hierarchicalResults: List<HierarchicalPreference>,
        mainCategory: PreferenceCategory,
        categoryKey: String,
        query: String
    ) {
        var currentParentCategory: PreferenceCategory? = null
        var currentParentHasChildren = false
        
        hierarchicalResults.forEach { hierPref ->
            val pref = hierPref.preference
            
            if (pref is PreferenceCategory) {
                if (currentParentCategory != null && currentParentHasChildren) {
                }
                
                currentParentCategory = PreferenceCategory(requireContext()).apply {
                    title = pref.title
                    key = "search_sub_${pref.key}_${categoryKey}"
                }
                currentParentHasChildren = false
                
            } else {
                val clonedPref = clonePreference(pref, categoryKey)
                
                if (currentParentCategory != null && !currentParentHasChildren) {
                    mainCategory.addPreference(currentParentCategory!!)
                    currentParentHasChildren = true
                }
                
                if (currentParentCategory != null) {
                    currentParentCategory!!.addPreference(clonedPref)
                } else {
                    mainCategory.addPreference(clonedPref)
                }
            }
        }
    }

    private fun clonePreference(original: Preference, categoryKey: String): Preference {
        val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val key = original.key ?: ""

        val cloned: Preference = when (original) {
            is androidx.preference.SwitchPreferenceCompat -> {
                androidx.preference.SwitchPreferenceCompat(requireContext()).also { sw ->
                    // Read actual saved value from SharedPreferences, not from the temp-inflated pref
                    sw.isChecked = sharedPrefs.getBoolean(key, false)
                    sw.summaryOn = original.summaryOn
                    sw.summaryOff = original.summaryOff
                    sw.setOnPreferenceChangeListener { _, newValue ->
                        sharedPrefs.edit().putBoolean(key, newValue as Boolean).apply()
                        true
                    }
                }
            }
            is androidx.preference.SwitchPreference -> {
                androidx.preference.SwitchPreference(requireContext()).also { sw ->
                    sw.isChecked = sharedPrefs.getBoolean(key, false)
                    sw.summaryOn = original.summaryOn
                    sw.summaryOff = original.summaryOff
                    sw.setOnPreferenceChangeListener { _, newValue ->
                        sharedPrefs.edit().putBoolean(key, newValue as Boolean).apply()
                        true
                    }
                }
            }
            is androidx.preference.CheckBoxPreference -> {
                androidx.preference.CheckBoxPreference(requireContext()).also { cb ->
                    cb.isChecked = sharedPrefs.getBoolean(key, false)
                    cb.summaryOn = original.summaryOn
                    cb.summaryOff = original.summaryOff
                    cb.setOnPreferenceChangeListener { _, newValue ->
                        sharedPrefs.edit().putBoolean(key, newValue as Boolean).apply()
                        true
                    }
                }
            }
            is androidx.preference.ListPreference -> {
                androidx.preference.ListPreference(requireContext()).also { lp ->
                    lp.entries = original.entries
                    lp.entryValues = original.entryValues
                    // Read actual saved value
                    val savedValue = sharedPrefs.getString(key, null)
                    lp.value = savedValue
                    // Show the currently selected entry as summary
                    val idx = lp.entryValues?.indexOf(savedValue) ?: -1
                    if (idx >= 0) lp.summary = lp.entries[idx]
                    else lp.summary = original.summary
                    lp.setOnPreferenceChangeListener { pref, newValue ->
                        sharedPrefs.edit().putString(key, newValue as String).apply()
                        val newIdx = lp.entryValues?.indexOf(newValue) ?: -1
                        if (newIdx >= 0) pref.summary = lp.entries[newIdx]
                        true
                    }
                }
            }
            is androidx.preference.MultiSelectListPreference -> {
                androidx.preference.MultiSelectListPreference(requireContext()).also { ml ->
                    ml.entries = original.entries
                    ml.entryValues = original.entryValues
                    ml.values = sharedPrefs.getStringSet(key, emptySet())
                    ml.setOnPreferenceChangeListener { _, newValue ->
                        @Suppress("UNCHECKED_CAST")
                        sharedPrefs.edit().putStringSet(key, newValue as Set<String>).apply()
                        true
                    }
                }
            }
            is androidx.preference.EditTextPreference -> {
                androidx.preference.EditTextPreference(requireContext()).also { et ->
                    val savedText = sharedPrefs.getString(key, "")
                    et.text = savedText
                    et.summary = if (savedText.isNullOrEmpty()) original.summary else savedText
                    et.dialogTitle = original.dialogTitle
                    et.dialogMessage = original.dialogMessage
                    et.setOnPreferenceChangeListener { pref, newValue ->
                        val text = newValue as String
                        sharedPrefs.edit().putString(key, text).apply()
                        pref.summary = text.ifEmpty { original.summary }
                        true
                    }
                }
            }
            is androidx.preference.SeekBarPreference -> {
                androidx.preference.SeekBarPreference(requireContext()).also { sb ->
                    sb.min = original.min
                    sb.max = original.max
                    sb.seekBarIncrement = original.seekBarIncrement
                    sb.showSeekBarValue = original.showSeekBarValue
                    sb.value = sharedPrefs.getInt(key, original.min)
                    sb.setOnPreferenceChangeListener { _, newValue ->
                        sharedPrefs.edit().putInt(key, newValue as Int).apply()
                        true
                    }
                }
            }
            else -> {
                // Unknown/complex type: just navigate
                Preference(requireContext()).also { p ->
                    p.setOnPreferenceClickListener {
                        navigateToPreferenceLocation(original.key, categoryKey)
                        true
                    }
                }
            }
        }

        // Copy shared visual properties
        cloned.key = key
        cloned.title = original.title
        if (cloned.summary.isNullOrEmpty()) cloned.summary = original.summary
        cloned.icon = original.icon
        cloned.isEnabled = original.isEnabled
        cloned.isSelectable = true
        cloned.isPersistent = false

        // For every interactive type, show a snackbar with a "Go to" action on every click.
        // Returning false means the preference still handles its own click too
        // (switch toggles, list opens its dialog, edittext opens its dialog, etc.)
        when (cloned) {
            is androidx.preference.SwitchPreferenceCompat,
            is androidx.preference.SwitchPreference,
            is androidx.preference.CheckBoxPreference,
            is androidx.preference.ListPreference,
            is androidx.preference.MultiSelectListPreference,
            is androidx.preference.EditTextPreference,
            is androidx.preference.SeekBarPreference -> {
                cloned.setOnPreferenceClickListener {
                    val categoryName = getString(categoryTitles[categoryKey] ?: R.string.settings)
                    Snackbar.make(requireView(), "${original.title} • $categoryName", Snackbar.LENGTH_LONG)
                        .setAction(getString(android.R.string.ok).replace("OK", "Go →")) {
                            navigateToPreferenceLocation(original.key, categoryKey)
                        }
                        .show()
                    false // false = also let the preference handle the click (toggle/open dialog)
                }
            }
            else -> { /* plain Preference already has its listener set above */ }
        }

        return cloned
    }

    private fun showNavigationPrompt(pref: Preference, categoryKey: String) {
        val categoryName = getString(categoryTitles[categoryKey] ?: R.string.settings)
        Snackbar.make(requireView(), "${pref.title} • $categoryName", Snackbar.LENGTH_LONG)
            .setAction("Go →") {
                navigateToPreferenceLocation(pref.key, categoryKey)
            }
            .show()
    }
    
    private fun navigateToPreferenceLocation(prefKey: String?, categoryKey: String) {
        try {
            (activity as? SettingsActivity)?.clearSearchFocus()
            
            hideKeyboard()
            
            restoreNormalView()
            
            val navController = findNavController()
            val action = categoryNavigationActions[categoryKey]
            
            if (action != null) {
                val bundle = Bundle().apply {
                    putString(ARG_HIGHLIGHT_KEY, prefKey)
                    putBoolean(ARG_RETURN_TO_SEARCH, false)
                }
                navController.navigate(action, bundle)
            }
        } catch (e: Exception) {
            Log.e("MainSettings", "Error navigating to preference location", e)
            Toast.makeText(requireContext(), "Navigation failed", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideKeyboard() {
        val activity = requireActivity()
        val imm = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        activity.currentFocus?.let { view ->
            imm?.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    private fun hideEmptyCategoriesInMain() {
        findPreference<PreferenceCategory>("backup_restore")?.let { category ->
            val hasVisibleChildren = (0 until category.preferenceCount).any {
                category.getPreference(it).isVisible
            }
            category.isVisible = hasVisibleChildren
        }
    }
    
    private val backupPathResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    activity?.contentResolver?.takePersistableUriPermission(
                        it,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    editor.putString("backup_path", it.toString())
                    editor.apply()
                    backupPath?.summary = FileUtil.formatPath(it.toString())
                }
            }
        }
    
    private val appRestoreResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let {
                    val contentResolver = requireContext().contentResolver
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    val reader = BufferedReader(InputStreamReader(contentResolver.openInputStream(it)))
                    val content = reader.readText()

                    runCatching {
                        val json: JsonObject = JsonParser.parseString(content).asJsonObject
                        val parsedDataMessage = StringBuilder()
                        val restoreData = RestoreAppDataItem()
                        
                        if (json.has("settings")) {
                            val settings = json.getAsJsonObject("settings")
                            restoreData.settings = settings.entrySet().map {
                                val value = it.value.asJsonPrimitive.toString().replace("\"", "")
                                val type = when(value) {
                                    "true", "false" -> "boolean"
                                    else -> "string"
                                }
                                BackupSettingsItem(it.key, value, type)
                            }
                            parsedDataMessage.appendLine("${getString(R.string.settings)}: ${settings.size()}")
                        }

                        if (json.has("history")) {
                            restoreData.downloads = json.getAsJsonArray("history").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), HistoryItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.downloads)}: ${restoreData.downloads!!.size}")
                        }

                        if (json.has("queued")) {
                            restoreData.queued = json.getAsJsonArray("queued").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.queue)}: ${restoreData.queued!!.size}")
                        }

                        if (json.has("scheduled")) {
                            restoreData.scheduled = json.getAsJsonArray("scheduled").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.scheduled)}: ${restoreData.scheduled!!.size}")
                        }

                        if (json.has("cancelled")) {
                            restoreData.cancelled = json.getAsJsonArray("cancelled").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.cancelled)}: ${restoreData.cancelled!!.size}")
                        }

                        if (json.has("errored")) {
                            restoreData.errored = json.getAsJsonArray("errored").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.errored)}: ${restoreData.errored!!.size}")
                        }

                        if (json.has("saved")) {
                            restoreData.saved = json.getAsJsonArray("saved").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), DownloadItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.saved)}: ${restoreData.saved!!.size}")
                        }

                        if (json.has("cookies")) {
                            restoreData.cookies = json.getAsJsonArray("cookies").map {
                                val item = Gson().fromJson(it.toString().replace("^\"|\"$", ""), CookieItem::class.java)
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.cookies)}: ${restoreData.cookies!!.size}")
                        }

                        if (json.has("templates")) {
                            restoreData.templates = json.getAsJsonArray("templates").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    CommandTemplate::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.command_templates)}: ${restoreData.templates!!.size}")
                        }

                        if (json.has("shortcuts")) {
                            restoreData.shortcuts = json.getAsJsonArray("shortcuts").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    TemplateShortcut::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.shortcuts)}: ${restoreData.shortcuts!!.size}")
                        }

                        if (json.has("search_history")) {
                            restoreData.searchHistory = json.getAsJsonArray("search_history").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    SearchHistoryItem::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.search_history)}: ${restoreData.searchHistory!!.size}")
                        }

                        if (json.has("observe_sources")) {
                            restoreData.observeSources = json.getAsJsonArray("observe_sources").map {
                                val item = Gson().fromJson(
                                    it.toString().replace("^\"|\"$", ""),
                                    ObserveSourcesItem::class.java
                                )
                                item.id = 0L
                                item
                            }
                            parsedDataMessage.appendLine("${getString(R.string.observe_sources)}: ${restoreData.observeSources!!.size}")
                        }

                        showAppRestoreInfoDialog(
                            onMerge = {
                                lifecycleScope.launch {
                                    val res = withContext(Dispatchers.IO){
                                        settingsViewModel.restoreData(restoreData, requireContext())
                                    }
                                    if (res) {
                                        showRestoreFinishedDialog(restoreData, parsedDataMessage.toString())
                                    }else{
                                        throw Error()
                                    }
                                }
                            },
                            onReset =  {
                                lifecycleScope.launch {
                                    val res = withContext(Dispatchers.IO){
                                        settingsViewModel.restoreData(restoreData, requireContext(),true)
                                    }
                                    if (res) {
                                        showRestoreFinishedDialog(restoreData, parsedDataMessage.toString())
                                    }else{
                                        throw Error()
                                    }
                                }
                            }
                        )

                    }.onFailure {
                        it.printStackTrace()
                        Snackbar.make(requireView(), it.message.toString(), Snackbar.LENGTH_INDEFINITE).show()
                    }
                }

            }
        }

    @SuppressLint("RestrictedApi")
    private fun showAppRestoreInfoDialog(onMerge: () -> Unit, onReset: () -> Unit){
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage(getString(R.string.restore_info))

        builder.setNegativeButton(getString(R.string.cancel)) { dialog : DialogInterface?, _: Int ->
            dialog?.dismiss()
        }

        builder.setPositiveButton(getString(R.string.restore)) { dialog : DialogInterface?, _: Int ->
            onMerge()
            dialog?.dismiss()
        }

        builder.setNeutralButton(getString(R.string.reset)) { dialog : DialogInterface?, _: Int ->
            onReset()
            dialog?.dismiss()
        }

        val dialog = builder.create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).gravity = Gravity.START
    }

    private fun showRestoreFinishedDialog(data: RestoreAppDataItem, restoreDataMessage: String) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.restore))
        builder.setMessage("${getString(R.string.restore_complete)}\n\n$restoreDataMessage")
        builder.setCancelable(false)
        builder.setPositiveButton(
            getString(R.string.ok)
        ) { _: DialogInterface?, _: Int ->
            if(data.settings != null){
                val languageValue = preferences.getString("app_language", "en")
                if (languageValue == "system") {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(null))
                }else{
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageValue.toString()))
                }
            }
            ThemeUtil.recreateAllActivities()
        }

        val dialog = builder.create()
        dialog.show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        searchManager.clearCache()
    }
}
