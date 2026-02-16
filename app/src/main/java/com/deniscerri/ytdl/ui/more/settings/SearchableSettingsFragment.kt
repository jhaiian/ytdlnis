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
    private val MAX_HIGHLIGHT_RETRIES = 5
    
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
            highlightPreference(key)
        }
    }
    
    private fun highlightPreference(key: String) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!isAdded || view == null) {
                    return@postDelayed
                }
                val preference = findPreference<Preference>(key)
                if (preference != null) {
                    expandParentCategory(preference)
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isAdded || view == null) return@postDelayed
                        scrollToPreferenceCenter(preference)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isAdded && view != null) {
                                highlightPreferenceView(preference)
                                highlightKey = null
                            }
                        }, 600)
                    }, 100)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 300)
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
                return
            }
            
            val recyclerView = listView as? RecyclerView ?: return
            if (!recyclerView.isLaidOut) {
                recyclerView.post {
                    highlightPreferenceView(preference)
                }
                return
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
                        highlightRetryCount = 0
                        return
                    }
                }
            }
            
            highlightRetryCount++
            recyclerView.postDelayed({
                if (isAdded && view != null) {
                    highlightPreferenceView(preference)
                }
            }, 200)
        } catch (e: Exception) {
            e.printStackTrace()
            highlightRetryCount = 0
        }
    }
    
    private fun animateHighlight(view: View, preference: Preference) {
        try {
            if (!isAdded || this.view == null) return
            
            val isSwitchPreference = preference is androidx.preference.SwitchPreferenceCompat || 
                                     preference is androidx.preference.SwitchPreference
            
            if (isSwitchPreference) {
                val targetView = view
                targetView.isPressed = true
                targetView.postDelayed({
                    if (isAdded) {
                        targetView.isPressed = false
                    }
                }, 200)
                
                targetView.postDelayed({
                    if (isAdded) {
                        targetView.isPressed = true
                        targetView.postDelayed({ 
                            if (isAdded) targetView.isPressed = false 
                        }, 200)
                    }
                }, 400)
                
                targetView.postDelayed({
                    if (isAdded) {
                        targetView.isPressed = true
                        targetView.postDelayed({ 
                            if (isAdded) targetView.isPressed = false 
                        }, 200)
                    }
                }, 800)
            } else {
                val transparentColor = Color.TRANSPARENT
                val highlightColor = Color.parseColor("#33000000")
                
                animateBackgroundColor(view, transparentColor, highlightColor, 150) {
                    animateBackgroundColor(view, highlightColor, transparentColor, 150) {
                        view.postDelayed({
                            if (isAdded) {
                                animateBackgroundColor(view, transparentColor, highlightColor, 150) {
                                    animateBackgroundColor(view, highlightColor, transparentColor, 150) {
                                        view.postDelayed({
                                            if (isAdded) {
                                                animateBackgroundColor(view, transparentColor, highlightColor, 150) {
                                                    animateBackgroundColor(view, highlightColor, transparentColor, 150)
                                                }
                                            }
                                        }, 150)
                                    }
                                }
                            }
                        }, 150)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun animateBackgroundColor(view: View, fromColor: Int, toColor: Int, duration: Long, onEnd: (() -> Unit)? = null) {
        try {
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
                override fun onAnimationCancel(animation: android.animation.Animator) {}
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
        highlightKey = null
        highlightRetryCount = 0
        
        if (shouldReturnToSearch) {
            findNavController().popBackStack()
            return true
        }
        return false
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        highlightKey = null
        highlightRetryCount = 0
    }
}
