package com.deniscerri.ytdl.ui.more.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.graphics.Color
import android.os.Bundle
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
    private var currentOverlay: View? = null
    
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
        view?.postDelayed({
            if (!isAdded || view == null) return@postDelayed
            val preference = findPreference<Preference>(key) ?: return@postDelayed
            expandParentCategory(preference)
            view?.postDelayed({
                if (!isAdded || view == null) return@postDelayed
                scrollToPreferenceCenter(preference)
                view?.postDelayed({
                    if (isAdded && view != null) {
                        highlightPreferenceView(preference)
                        highlightKey = null
                    }
                }, 600)
            }, 100)
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
        if (!isAdded || view == null) return
        
        if (highlightRetryCount >= MAX_HIGHLIGHT_RETRIES) {
            highlightRetryCount = 0
            return
        }
        
        val recyclerView = listView as? RecyclerView ?: return
        if (!recyclerView.isLaidOut) {
            recyclerView.post { highlightPreferenceView(preference) }
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
                    if (child.isAttachedToWindow) {
                        animateHighlight(child, preference)
                        highlightRetryCount = 0
                    } else {
                        child.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: View) {
                                v.removeOnAttachStateChangeListener(this)
                                animateHighlight(v, preference)
                            }
                            override fun onViewDetachedFromWindow(v: View) {}
                        })
                    }
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
    }
    
    private fun animateHighlight(view: View, preference: Preference) {
        if (!isAdded || !view.isAttachedToWindow) return
        
        val isSwitchPreference = preference is androidx.preference.SwitchPreferenceCompat ||
                preference is androidx.preference.SwitchPreference
        
        if (isSwitchPreference) {
            animateSwitchHighlight(view)
        } else {
            animateOverlayHighlight(view)
        }
    }
    
    private fun animateSwitchHighlight(view: View) {
        val switchView = findSwitchInView(view) ?: view
        listOf(0L, 200L, 400L, 600L).forEach { delay ->
            switchView.postDelayed({
                if (isAdded && switchView.isAttachedToWindow) {
                    switchView.isPressed = true
                    switchView.postDelayed({
                        if (isAdded) switchView.isPressed = false
                    }, 150)
                }
            }, delay)
        }
    }
    
    private fun animateOverlayHighlight(view: View) {
        val parent = view.parent as? ViewGroup ?: return
        val overlay = View(requireContext()).apply {
            setBackgroundColor(Color.parseColor("#33000000"))
            alpha = 0f
            layoutParams = ViewGroup.LayoutParams(view.width, view.height)
        }
        currentOverlay = overlay
        
        val index = parent.indexOfChild(view)
        parent.addView(overlay, index, view.layoutParams)
        
        fun removeOverlay() {
            if (overlay.parent != null) {
                parent.removeView(overlay)
            }
            if (currentOverlay == overlay) {
                currentOverlay = null
            }
        }
        
        overlay.animate()
            .alpha(0.4f)
            .setDuration(150)
            .withEndAction {
                overlay.animate()
                    .alpha(0f)
                    .setDuration(150)
                    .withEndAction {
                        overlay.animate()
                            .alpha(0.4f)
                            .setDuration(150)
                            .withEndAction {
                                overlay.animate()
                                    .alpha(0f)
                                    .setDuration(150)
                                    .withEndAction {
                                        overlay.animate()
                                            .alpha(0.4f)
                                            .setDuration(150)
                                            .withEndAction {
                                                overlay.animate()
                                                    .alpha(0f)
                                                    .setDuration(150)
                                                    .withEndAction {
                                                        removeOverlay()
                                                    }
                                            }
                                    }
                            }
                    }
            }
            .start()
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
        currentOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            currentOverlay = null
        }
        if (shouldReturnToSearch) {
            findNavController().popBackStack()
            return true
        }
        return false
    }
    
    override fun onDestroyView() {
        currentOverlay?.let {
            (it.parent as? ViewGroup)?.removeView(it)
            currentOverlay = null
        }
        highlightKey = null
        highlightRetryCount = 0
        super.onDestroyView()
    }
}
