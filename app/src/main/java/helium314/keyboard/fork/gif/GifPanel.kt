// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.fork.gif

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import helium314.keyboard.fork.toolbar.ForkTallPanel
import helium314.keyboard.keyboard.KeyboardTypeface
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import java.util.concurrent.Executors

/**
 * fork: GIFs in place of the letters, like the clipboard panel: a grid of animated previews, a star on each to keep
 * it as a favorite. What it shows (trending, a search, recent, favorites) is set by the toolbar through [show].
 * It can be made taller by swiping up on the toolbar, like the clipboard and emoji panels.
 */
@SuppressLint("ViewConstructor")
class GifPanel(
    context: Context,
    private val keyboardView: View,
    private val prefs: SharedPreferences,
    private val onPick: (GifItem) -> Unit,
) : FrameLayout(context), ForkTallPanel {
    private val density = resources.displayMetrics.density
    private val palette = Settings.getValues().mColors
    private var expandedHeight = 0
    private var items: List<GifItem> = emptyList()
    private val adapter = Adapter()
    private val grid = RecyclerView(context)
    private val status = TextView(context)
    /** "Powered by GIPHY / KLIPY", which the providers ask for wherever their GIFs are shown */
    private val attribution = TextView(context)

    init {
        setBackgroundColor(palette.get(ColorType.MAIN_BACKGROUND))
        isClickable = true // the keys below get no touches
        grid.layoutManager = StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL)
        grid.adapter = adapter
        grid.clipToPadding = false
        val pad = (4 * density).toInt()
        grid.setPadding(pad, pad, pad, pad)
        addView(grid, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        status.gravity = Gravity.CENTER
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        status.setTextColor(palette.get(ColorType.KEY_HINT_TEXT))
        KeyboardTypeface.applyToTextView(status)
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        attribution.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        attribution.setTextColor(palette.get(ColorType.KEY_TEXT))
        attribution.background = GradientDrawable().apply {
            cornerRadius = 6 * density
            setColor(palette.get(ColorType.MAIN_BACKGROUND) and 0x00FFFFFF or (0xD0 shl 24))
        }
        val apad = (5 * density).toInt()
        attribution.setPadding(apad, apad / 3, apad, apad / 3)
        attribution.text = GifClient.providerName(prefs)?.let { "Powered by $it" }
        attribution.visibility = if (attribution.text.isNullOrEmpty()) GONE else VISIBLE
        addView(attribution, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END)
            .apply { setMargins(0, 0, (8 * density).toInt(), (6 * density).toInt()) })
    }

    var isLoading = false
        private set

    /** [list] null while loading; [empty] shown when there is nothing (or while loading) */
    @SuppressLint("NotifyDataSetChanged")
    fun show(list: List<GifItem>?, empty: String) {
        isLoading = list == null
        items = list.orEmpty()
        if (grid.adapter !== adapter) grid.adapter = adapter
        adapter.notifyDataSetChanged()
        grid.scrollToPosition(0)
        status.text = empty
        status.visibility = if (items.isEmpty()) VISIBLE else GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // as tall as the letters, or taller when swiped up
        val height = if (expandedHeight > 0) expandedHeight else keyboardView.measuredHeight
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        val columns = (MeasureSpec.getSize(widthMeasureSpec) / (130 * density)).toInt().coerceIn(2, 5)
        (grid.layoutManager as StaggeredGridLayoutManager).let { if (it.spanCount != columns) it.spanCount = columns }
    }

    override fun forkNormalHeight() = keyboardView.measuredHeight
    override fun forkCurrentHeight() = height
    override fun setForkExpandedHeight(height: Int) {
        expandedHeight = height
        requestLayout()
    }

    private inner class Holder(val frame: FrameLayout, val image: ImageView, val star: ImageView) : RecyclerView.ViewHolder(frame)

    private inner class Adapter : RecyclerView.Adapter<Holder>() {
        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val frame = FrameLayout(context)
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = context.getString(R.string.fork_toolbar_gif)
                clipToOutline = true
                background = GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(palette.get(ColorType.KEY_BACKGROUND))
                }
            }
            frame.addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            // star on a dark round spot, so it shows on any GIF
            val star = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                val p = (5 * density).toInt()
                setPadding(p, p, p, p)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0x66000000)
                }
            }
            frame.addView(star, LayoutParams((28 * density).toInt(), (28 * density).toInt(), Gravity.TOP or Gravity.END).apply {
                topMargin = (4 * density).toInt()
                marginEnd = (4 * density).toInt()
            })
            frame.layoutParams = StaggeredGridLayoutManager.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                val m = (3 * density).toInt()
                setMargins(m, m, m, m)
            }
            return Holder(frame, image, star)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            // height from the GIF's shape and the column width
            val lm = grid.layoutManager as StaggeredGridLayoutManager
            val columnWidth = ((grid.width - grid.paddingLeft - grid.paddingRight).coerceAtLeast(1) / lm.spanCount) - (6 * density).toInt()
            holder.image.layoutParams = holder.image.layoutParams.apply {
                height = (columnWidth / item.aspectRatio.coerceIn(0.5f, 2.5f)).toInt().coerceAtLeast((40 * density).toInt())
            }
            holder.frame.setOnClickListener { onPick(item) }
            bindStar(holder.star, GifStore.isFavorite(prefs, item))
            holder.star.setOnClickListener { bindStar(holder.star, GifStore.toggleFavorite(prefs, item)) }
            load(holder.image, item.previewUrl)
        }

        override fun onViewRecycled(holder: Holder) {
            holder.image.tag = null
            holder.image.setImageDrawable(null)
        }
    }

    private fun bindStar(star: ImageView, favorite: Boolean) {
        star.setImageResource(if (favorite) helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_star_filled) else helium314.keyboard.fork.DotIcons.of(R.drawable.ic_dot_star))
        star.setColorFilter(if (favorite) palette.get(ColorType.ACTION_KEY_BACKGROUND) else 0xFFFFFFFF.toInt())
        star.contentDescription = context.getString(if (favorite) R.string.fork_gif_unfavorite else R.string.fork_gif_favorite)
    }

    /** the preview, animated from Android 9 on (first frame before); the files are kept for a while */
    private fun load(view: ImageView, url: String) {
        view.tag = url
        loader.execute {
            val bytes = cache.get(url) ?: runCatching { GifClient.bytes(url) }.getOrNull()?.also { cache.put(url, it) } ?: return@execute
            val drawable: Drawable? = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    android.graphics.ImageDecoder.decodeDrawable(android.graphics.ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))
                } else {
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?.let { android.graphics.drawable.BitmapDrawable(resources, it) }
                }
            }.getOrNull()
            if (drawable != null) view.post {
                if (view.tag != url || !view.isAttachedToWindow) return@post
                view.setImageDrawable(drawable)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && drawable is android.graphics.drawable.AnimatedImageDrawable)
                    drawable.start()
            }
        }
    }

    companion object {
        private val loader = Executors.newFixedThreadPool(4)
        /** preview files, about 12 MB */
        private val cache = object : LruCache<String, ByteArray>(12 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
    }
}
