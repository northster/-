// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.keyboard.clipboard

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.latin.ClipboardHistoryEntry
import helium314.keyboard.latin.ClipboardHistoryManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings

class ClipboardAdapter(
       val clipboardLayoutParams: ClipboardLayoutParams,
       val keyEventListener: OnKeyEventListener
) : RecyclerView.Adapter<ClipboardAdapter.ViewHolder>() {

    var clipboardHistoryManager: ClipboardHistoryManager? = null

    var pinnedIconResId = 0
    var itemBackgroundId = 0
    var itemTypeFace: Typeface? = null
    var itemTextColor = 0
    var itemTextSize = 0f
    /** fork: max lines of text on a card, see ClipPrefs.PREVIEW_LINES */
    var itemMaxLines = 4

    /** fork: clips are being picked for deletion: a tap selects instead of pasting */
    var selecting = false
        private set
    val selected = LinkedHashSet<Long>()
    var onSelectionChanged: () -> Unit = {}

    @SuppressLint("NotifyDataSetChanged")
    fun setSelecting(on: Boolean) {
        selecting = on
        selected.clear()
        notifyDataSetChanged()
        onSelectionChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectAll(all: Boolean) {
        selected.clear()
        if (all) for (i in 0 until itemCount) getItem(i)?.let { selected.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged()
    }

    private fun toggleSelected(id: Long, position: Int) {
        if (!selected.remove(id)) selected.add(id)
        notifyItemChanged(position)
        onSelectionChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.clipboard_entry_key, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.setContent(getItem(position))
    }

    private fun getItem(position: Int) = clipboardHistoryManager?.getHistoryEntry(position)

    override fun getItemCount() = clipboardHistoryManager?.getHistorySize() ?: 0

    inner class ViewHolder(
            view: View
    ) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

        private val pinnedIconView: ImageView
        private val contentTextView: TextView
        private val contentImageView: ImageView
        private val pinButton: ImageView
        private val deleteButton: ImageView

        init {
            view.apply {
                setOnClickListener(this@ViewHolder)
                setOnTouchListener(this@ViewHolder)
                setOnLongClickListener(this@ViewHolder)
                setBackgroundResource(itemBackgroundId)
                isHapticFeedbackEnabled = false
            }
            Settings.getValues().mColors.setBackground(view, ColorType.KEY_BACKGROUND)
            // fork: the pin toggle at the top left (a check mark while selecting)
            pinnedIconView = view.findViewById<ImageView>(R.id.clipboard_entry_pinned_icon).apply {
                setOnClickListener {
                    val id = view.tag as? Long ?: return@setOnClickListener
                    if (selecting) toggleSelected(id, bindingAdapterPosition) else keyEventListener.onTogglePin(id)
                }
            }
            contentTextView = view.findViewById<TextView>(R.id.clipboard_entry_text_content).apply {
                typeface = itemTypeFace
                setTextColor(itemTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, itemTextSize)
            }
            contentImageView = view.findViewById(R.id.clipboard_entry_image_content)
            clipboardLayoutParams.setItemProperties(view)
            val colors = Settings.getValues().mColors
            pinButton = view.findViewById<ImageView>(R.id.clipboard_entry_pin).apply {
                setOnClickListener { (view.tag as? Long)?.let { keyEventListener.onTogglePin(it) } }
            }
            deleteButton = view.findViewById<ImageView>(R.id.clipboard_entry_delete).apply {
                setOnClickListener { (view.tag as? Long)?.let { keyEventListener.onDeleteClip(it) } }
            }
            colors.setColor(deleteButton, ColorType.KEY_HINT_TEXT)
        }

        fun setContent(historyEntry: ClipboardHistoryEntry?) {
            if (historyEntry == null) return
            itemView.tag = historyEntry.id
            contentTextView.maxLines = itemMaxLines
            if (historyEntry.filename != null) {
                historyEntry.setImageAndDescription(contentImageView, contentTextView)
            } else {
                // truncate displayed text for performance reasons; fork: the first line starts after the pin lying
                //  over the corner, without a blank line for it
                contentTextView.text = historyEntry.text?.take(1000)?.let { text ->
                    android.text.SpannableString(text).apply {
                        val indent = (16 * contentTextView.resources.displayMetrics.density).toInt()
                        setSpan(android.text.style.LeadingMarginSpan.Standard(indent, 0), 0, length, 0)
                    }
                }
                // fork: Korean font for clips with Hangul
                contentTextView.typeface = helium314.keyboard.keyboard.KeyboardTypeface.resolve(contentTextView.text,
                    itemTypeFace ?: Typeface.DEFAULT)
            }
            // fork: pin toggle at the top left, in the enter key's color when pinned; a check mark while selecting
            val colors = Settings.getValues().mColors
            if (selecting) {
                val isSelected = historyEntry.id in selected
                pinnedIconView.setImageResource(R.drawable.ic_dot_check)
                colors.setColor(pinnedIconView, if (isSelected) ColorType.ACTION_KEY_BACKGROUND else ColorType.KEY_HINT_TEXT)
                pinnedIconView.alpha = if (isSelected) 1f else 0.35f
                itemView.alpha = if (isSelected) 1f else 0.75f
            } else {
                // pinned: filled in the enter key's color
                pinnedIconView.setImageResource(if (historyEntry.isPinned) R.drawable.ic_dot_pin_filled else R.drawable.ic_dot_pin)
                colors.setColor(pinnedIconView, if (historyEntry.isPinned) ColorType.ACTION_KEY_BACKGROUND else ColorType.KEY_HINT_TEXT)
                pinnedIconView.alpha = if (historyEntry.isPinned) 1f else 0.45f
                itemView.alpha = 1f
            }
            pinnedIconView.visibility = View.VISIBLE
            contentImageView.visibility = if (historyEntry.filename != null) View.VISIBLE else View.GONE
            contentTextView.visibility = if (contentTextView.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                keyEventListener.onKeyDown(view.tag as Long)
            }
            return false
        }

        override fun onClick(view: View) {
            if (selecting) { toggleSelected(view.tag as Long, bindingAdapterPosition); return }
            keyEventListener.onKeyUp(view.tag as Long)
        }

        override fun onLongClick(view: View): Boolean {
            if (selecting) { toggleSelected(view.tag as Long, bindingAdapterPosition); return true }
            keyEventListener.onLongPressClip(view.tag as Long) // fork: info panel instead of toggling pin
            return true
        }
    }
}
