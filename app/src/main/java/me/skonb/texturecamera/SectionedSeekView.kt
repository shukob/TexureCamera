package me.skonb.texturecamera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Created by skonb on 2016/08/01.
 */


class SectionedSeekView : View {

    interface Delegate {
        fun onReachMaximumLength(sectionedSeekView: SectionedSeekView)
    }

    constructor(context: Context?) : super(context)

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var maximumLength: Double = 0.0
    var sections: MutableList<Double> = arrayListOf()
    var delegate: Delegate? = null
    var paint = Paint()

    val colors: IntArray = intArrayOf(
            Color.parseColor("#7fbfff"),
            Color.parseColor("#ff7fbf"),
            Color.parseColor("#bfff7f"),
            Color.parseColor("#ffff7f"),
            Color.parseColor("#bf7fff"),
            Color.parseColor("#fba848"))


    var totalLength: Double
        get() {
            return sections.fold(0.0) { x, y -> x + y }
        }
        set(value) {
            if (value <= 0) {
                return
            }
            if (value != sections.fold(0.0) { x, y -> x + y }) {
                if (sections.size > 1) {
                    val total = sections.slice(IntRange(0, sections.size - 2)).fold(0.0) { x, y -> x + y }
                    setLengthOfCurrentSection(value - total)
                } else {
                    setLengthOfCurrentSection(value)
                }
            }
            if (value >= maximumLength) {
                delegate?.onReachMaximumLength(this)
            }
        }

    fun setLengthOfCurrentSection(length: Double) {
        sections[sections.size - 1] = length
        postInvalidate()
    }

    fun addSection() {
        sections.add(0.0)
    }

    fun trimSection() {
        while (sections.indexOf(0.0) != -1) {
            sections.remove(0.0)
        }
    }

    fun clear() {
        sections.clear()
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas?) {

        super.onDraw(canvas)
        var offset = 0F
        var index = 0
        for (section in sections) {
            val width = (width * (section / maximumLength)).toFloat()
            paint.style = Paint.Style.FILL
            paint.color = colors[index % colors.count()]
            canvas?.drawRect(offset, 0F, offset + width, height.toFloat(), paint)
            offset += width
            ++index
        }
    }

}
