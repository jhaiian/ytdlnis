package com.deniscerri.ytdl.ui.more.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch

abstract class SearchableSettingsFragment : BaseSettingsFragment() {
    
    private var highlightKey: String? = null
    private var shouldReturnToSearch: Boolean = false
    private var highlightRetryCount = 0
    private val MAX_HIGHLIGHT_RETRIES = 8
    private var highlightHandler: Handler? = null
    private var isHighlighting = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            highlightKey = it.getString(MainSettingsFragment.ARG_HIGHLIGHT_KEY)
            shouldReturnToSearch = it.getBoolean(MainSettingsFragment.ARG_RETURN_TO_SEARCH, false)
        }
        highlightHandler = Handler(Looper.getMainLooper())
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        highlightKey?.let { key ->
            scheduleHighlight(key)
        }
    }
    
    private fun scheduleHighlight(key: String, initialDelay: Long = 300) {
        highlightHandler?.postDelayed({
            if (isAdded && view != null && !isHighlighting) {
                performHighlight(key)
            }
        }, initialDelay)
    }
    
    private fun performHighlight(key: String) {
        try {
            if (!isAdded || view == null || isHighlighting) {
                return
            }
            
            isHighlighting = true
            val preference = findPreference<Preference>(key)
            
            if (preference != null) {
                expandParentCategory(preference)
                
                highlightHandler?.postDelayed({
                    if (!isAdded || view == null) {
                        isHighlighting = false
                        return@postDelayed
                    }
                    
                    scrollToPreferenceCenter(preference)
                    
                    highlightHandler?.postDelayed({
                        if (isAdded && view != null) {
                            highlightPreferenceView(preference)
                        }
                        isHighlighting = false
                    }, 500)
                }, 150)
            } else {
                isHighlighting = false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isHighlighting = false
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
    
    private fun highlightPreferenceView(preference: Preference) {
        try {
            if (!isAdded || view == null) return
            
            if (highlightRetryCount >= MAX_HIGHLIGHT_RETRIES) {
                highlightRetryCount = 0
                highlightKey = null
                return
            }
            
            val recyclerView = listView as? RecyclerView ?: return
            
            if (!recyclerView.isLaidOut) {
                recyclerView.post {
                    highlightPreferenceView(preference)
                }
                return
            }
            
            var found = false
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
                        highlightRetryCount = 0
                        highlightKey = null
                        found = true
                        break
                    }
                }
            }
            
            if (!found) {
                highlightRetryCount++
                val retryDelay = 150L * (1 shl (highlightRetryCount / 2))
                
                recyclerView.postDelayed({
                    if (isAdded && view != null) {
                        highlightPreferenceView(preference)
                    }
                }, retryDelay.coerceAtMost(1000L))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            highlightRetryCount = 0
            highlightKey = null
        }
    }
    
    private fun animateHighlight(view: View, preference: Preference) {
        try {
            if (!isAdded || this.view == null) return
            
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            val isSwitchPreference = preference is androidx.preference.SwitchPreferenceCompat || 
                                     preference is androidx.preference.SwitchPreference
            
            if (isSwitchPreference) {
                animateSwitchHighlight(view)
            } else {
                animateBackgroundHighlight(view)
            }
            
            view.postDelayed({
                if (isAdded) {
                    view.setLayerType(View.LAYER_TYPE_NONE, null)
                }
            }, 1500)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun animateSwitchHighlight(targetView: View) {
        val pulseSequence = listOf(0L, 200L, 400L, 600L, 800L, 1000L)
        
        pulseSequence.forEach { delay ->
            targetView.postDelayed({
                if (isAdded) {
                    targetView.isPressed = true
                    targetView.postDelayed({ 
                        if (isAdded) targetView.isPressed = false 
                    }, 150)
                }
            }, delay)
        }
    }
    
    private fun animateBackgroundHighlight(view: View) {
        val transparentColor = Color.TRANSPARENT
        val highlightColor = Color.parseColor("#33000000")
        
        animateBackgroundColor(view, transparentColor, highlightColor, 120) {
            animateBackgroundColor(view, highlightColor, transparentColor, 120) {
                view.postDelayed({
                    if (isAdded) {
                        animateBackgroundColor(view, transparentColor, highlightColor, 120) {
                            animateBackgroundColor(view, highlightColor, transparentColor, 120) {
                                view.postDelayed({
                                    if (isAdded) {
                                        animateBackgroundColor(view, transparentColor, highlightColor, 120) {
                                            animateBackgroundColor(view, highlightColor, transparentColor, 120)
                                        }
                                    }
                                }, 100)
                            }
                        }
                    }
                }, 100)
            }
        }
    }
    
    private fun animateBackgroundColor(
        view: View, 
        fromColor: Int, 
        toColor: Int, 
        duration: Long, 
        onEnd: (() -> Unit)? = null
    ) {
        try {
            if (!isAdded) {
                onEnd?.invoke()
                return
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
                    onEnd?.invoke()
                }
                override fun onAnimationStart(animation: android.animation.Animator) {}
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    onEnd?.invoke()
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {}
            })
            
            colorAnimation.start()
        } catch (e: Exception) {
            e.printStackTrace()
            onEnd?.invoke()
        }
    }
    
    private fun findSwitchInView(view: View): View? {
        when (view) {
            is Switch, is SwitchCompat, is MaterialSwitch -> return view
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    val found = findSwitchInView(view.getChildAt(i))
                    if (found != null) return found
                }
            }
        }
        return null
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
        highlightRetryCount = 0
        isHighlighting = false
        highlightHandler?.removeCallbacksAndMessages(null)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        cleanup()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        highlightHandler = null
    }
}
