package com.deniscerri.ytdl.ui.more.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

abstract class SearchableSettingsFragment : BaseSettingsFragment() {
    
    private var highlightKey: String? = null
    private var shouldReturnToSearch: Boolean = false
    private var highlightJob: Job? = null
    
    companion object {
        private const val MAX_HIGHLIGHT_RETRIES = 8
        private const val INITIAL_HIGHLIGHT_DELAY = 300L
        private const val EXPAND_DELAY = 150L
        private const val SCROLL_DELAY = 500L
        private const val BASE_RETRY_DELAY = 150L
        private const val MAX_RETRY_DELAY = 1000L
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            highlightKey = it.getString(MainSettingsFragment.ARG_HIGHLIGHT_KEY)
            shouldReturnToSearch = it.getBoolean(MainSettingsFragment.ARG_RETURN_TO_SEARCH, false)
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        highlightKey?.let { key ->
            scheduleHighlight(key)
        }
    }
    
    private fun scheduleHighlight(key: String, delay: Long = INITIAL_HIGHLIGHT_DELAY) {
        highlightJob?.cancel()
        highlightJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(delay)
            if (isAdded && view != null) {
                performHighlight(key)
            }
        }
    }
    
    private suspend fun performHighlight(key: String, retryCount: Int = 0) {
        if (!isAdded || view == null || retryCount >= MAX_HIGHLIGHT_RETRIES) {
            cleanup()
            return
        }
        
        try {
            val preference = findPreference<Preference>(key)
            
            if (preference != null) {
                expandParentCategory(preference)
                delay(EXPAND_DELAY)
                
                if (!isAdded || view == null) return
                
                scrollToPreferenceCenter(preference)
                delay(SCROLL_DELAY)
                
                if (!isAdded || view == null) return
                
                val highlighted = highlightPreferenceView(preference)
                
                if (!highlighted && retryCount < MAX_HIGHLIGHT_RETRIES) {
                    val retryDelay = (BASE_RETRY_DELAY * (1 shl (retryCount / 2)))
                        .coerceAtMost(MAX_RETRY_DELAY)
                    delay(retryDelay)
                    performHighlight(key, retryCount + 1)
                } else {
                    cleanup()
                }
            } else {
                cleanup()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup()
        }
    }
    
    private fun scrollToPreferenceCenter(preference: Preference) {
        try {
            scrollToPreference(preference)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun expandParentCategory(preference: Preference) {
        var parent = preference.parent
        while (parent != null) {
            if (parent is androidx.preference.PreferenceCategory) {
                parent.isVisible = true
            }
            parent = parent.parent
        }
    }
    
    private suspend fun highlightPreferenceView(preference: Preference): Boolean {
        if (!isAdded || view == null) return false
        
        val recyclerView = listView as? RecyclerView ?: return false
        
        if (!recyclerView.isLaidOut) {
            delay(50)
            return false
        }
        
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i) ?: continue
            val holder = recyclerView.getChildViewHolder(child)
            val adapterPosition = holder.bindingAdapterPosition
            
            if (adapterPosition != RecyclerView.NO_POSITION) {
                val boundPreference = try {
                    val adapter = recyclerView.adapter
                    if (adapter != null && adapterPosition < adapter.itemCount) {
                        val method = adapter.javaClass.getMethod("getItem", Int::class.java)
                        method.invoke(adapter, adapterPosition) as? Preference
                    } else null
                } catch (e: Exception) {
                    null
                }
                
                if (boundPreference?.key == preference.key) {
                    animateHighlight(child, preference)
                    return true
                }
            }
        }
        
        return false
    }
    
    private fun animateHighlight(view: View, preference: Preference) {
        try {
            if (!isAdded || this.view == null) return
            
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val isSwitchPreference = preference is androidx.preference.SwitchPreferenceCompat ||
                    preference is androidx.preference.SwitchPreference
            
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (isSwitchPreference) {
                        animateSwitchHighlight(view)
                    } else {
                        animateBackgroundHighlight(view)
                    }
                    
                    delay(1500)
                    if (isAdded) {
                        view.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                } catch (e: CancellationException) {
                    if (isAdded) {
                        view.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private suspend fun animateSwitchHighlight(targetView: View) {
        if (!isAdded) return
        
        val pulseDelays = listOf(0L, 200L, 400L, 600L, 800L, 1000L)
        
        coroutineScope {
            pulseDelays.forEach { delayMs ->
                launch {
                    delay(delayMs)
                    if (isAdded) {
                        withContext(Dispatchers.Main) {
                            targetView.isPressed = true
                            delay(150)
                            if (isAdded) {
                                targetView.isPressed = false
                            }
                        }
                    }
                }
            }
        }
    }
    
    private suspend fun animateBackgroundHighlight(view: View) {
        if (!isAdded) return
        
        val transparentColor = Color.TRANSPARENT
        val highlightColor = Color.parseColor("#33000000")
        
        repeat(3) {
            animateBackgroundColor(view, transparentColor, highlightColor, 120)
            animateBackgroundColor(view, highlightColor, transparentColor, 120)
            if (it < 2) {
                delay(100)
            }
        }
    }
    
    private suspend fun animateBackgroundColor(
        view: View,
        fromColor: Int,
        toColor: Int,
        duration: Long
    ) = suspendCancellableCoroutine<Unit> { continuation ->
        if (!isAdded) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        
        val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), fromColor, toColor)
        colorAnimation.duration = duration
        
        colorAnimation.addUpdateListener { animator ->
            if (isAdded) {
                view.setBackgroundColor(animator.animatedValue as Int)
            }
        }
        
        colorAnimation.addListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            
            override fun onAnimationCancel(animation: android.animation.Animator) {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
            
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
        
        continuation.invokeOnCancellation {
            colorAnimation.cancel()
        }
        
        colorAnimation.start()
    }
    
    private fun findSwitchInView(view: View): View? {
        return when (view) {
            is Switch, is SwitchCompat, is MaterialSwitch -> view
            is ViewGroup -> {
                (0 until view.childCount)
                    .mapNotNull { findSwitchInView(view.getChildAt(it)) }
                    .firstOrNull()
            }
            else -> null
        }
    }
    
    open fun onBackPressed(): Boolean {
        cleanup()
        
        if (shouldReturnToSearch) {
            findNavController().popBackStack()
            return true
        }
        return false
    }
    
    private fun cleanup() {
        highlightKey = null
        highlightJob?.cancel()
        highlightJob = null
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cleanup()
    }
}
