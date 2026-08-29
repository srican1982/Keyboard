package com.personal.sinhalakeyboard

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class EmojiPanel(
    private val context: Context,
    private val root: View,
    private val onEmojiPicked: (String) -> Unit,
) {
    private val categoryBar: LinearLayout = root.findViewById(R.id.emojiCategoryBar)
    private val emojiGrid: GridLayout = root.findViewById(R.id.emojiGrid)
    private var selectedCategory = 0

    fun bind() {
        buildCategoryBar()
        showCategory(0)
    }

    private fun buildCategoryBar() {
        categoryBar.removeAllViews()
        EmojiData.categories.forEachIndexed { index, category ->
            val tab = TextView(context).apply {
                text = category.icon
                textSize = 22f
                gravity = Gravity.CENTER
                setPadding(20, 8, 20, 8)
                setOnClickListener {
                    selectedCategory = index
                    highlightTabs()
                    showCategory(index)
                }
            }
            categoryBar.addView(tab)
        }
        highlightTabs()
    }

    private fun highlightTabs() {
        for (i in 0 until categoryBar.childCount) {
            val tab = categoryBar.getChildAt(i) as TextView
            tab.alpha = if (i == selectedCategory) 1f else 0.45f
            tab.setTypeface(null, if (i == selectedCategory) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun showCategory(index: Int) {
        emojiGrid.removeAllViews()
        val emojis = EmojiData.categories[index].emojis
        val columnCount = 8
        emojis.forEachIndexed { i, emoji ->
            val cell = TextView(context).apply {
                text = emoji
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(4, 12, 4, 12)
                setOnClickListener {
                    Prefs.addRecentEmoji(context, emoji)
                    onEmojiPicked(emoji)
                }
            }
            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % columnCount, 1f)
                rowSpec = GridLayout.spec(i / columnCount)
            }
            emojiGrid.addView(cell, params)
        }
    }
}
