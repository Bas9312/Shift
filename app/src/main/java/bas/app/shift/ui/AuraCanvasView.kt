package bas.app.shift.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import bas.app.shift.R
import bas.app.shift.models.*
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.ImageLoader
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

class AuraCanvasView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private var aura: Aura? = null
    private var humanBitmap: Bitmap? = null
    private val markBitmaps = ConcurrentHashMap<String, Bitmap?>() // url -> bitmap
    private val imageLoader = ImageLoader(context)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Zoom/drag state
    private var scaleFactor = 0.7f
    private var lastScaleFactor = 0.7f
    private var focusX = 0f
    private var focusY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var offsetX = 0f
    private var offsetY = 0f
    private var isDragging = false
    private var pointerCount = 0

    init {
        humanBitmap = BitmapFactory.decodeResource(resources, R.drawable.human)
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setAura(aura: Aura?) {
        this.aura = aura
        // Кешируем картинки меток
        aura?.marks?.forEach { mark ->
            if (!markBitmaps.containsKey(mark.imageUrl)) {
                scope.launch {
                    val bmp = loadBitmap(mark.imageUrl)
                    if (bmp != null) {
                        markBitmaps[mark.imageUrl] = bmp
                    }
                    invalidate()
                }
            }
        }
        invalidate()
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = imageLoader.execute(request)
            if (result is SuccessResult) {
                // result.image: ImageBitmap
                result.image.toBitmap()
            } else null
        } catch (e: Exception) { null }
    }

    override fun onDraw(canvas: Canvas) {
        markTouchAreas.clear()
        super.onDraw(canvas)
        aura?.let { drawAura(canvas, it) }
    }

    private fun drawAura(canvas: Canvas, aura: Aura) {
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scaleFactor, scaleFactor, width / 2f, height / 2f)

        val centerX = width / 2f
        val centerY = height / 2f
        val humanBitmap = humanBitmap
        val humanWidth = (humanBitmap?.width?.toFloat() ?: 0f) * 0.6f
        val humanHeight = (humanBitmap?.height?.toFloat() ?: 0f) * 0.6f
        val humanRadius = max(humanWidth, humanHeight) / 2f
        val auraRadius = humanRadius * 1.7f

        // Аура (круг)
        val paintAura = Paint().apply {
            color = auraColor(aura.type, aura.percentOfHumanism)
            style = Paint.Style.FILL
            alpha = if (aura.auraHidden) 80 else 180
        }
        canvas.drawCircle(centerX, centerY, auraRadius, paintAura)

        // 10 слотов для проблем по кругу вокруг человека
        val slotsCount = 10
        val problemRadius = humanRadius * 0.13f
        val slotAngles = List(slotsCount) { i -> Math.toRadians((360.0 / slotsCount * i - 90.0)).toFloat() }
        val problemsBySlot = aura.auraProblems.orEmpty().associateBy { it.slot }
        for (slot in 0 until slotsCount) {
            val angle = slotAngles[slot]
            val px = centerX + cos(angle) * (humanRadius + problemRadius + 6f)
            val py = centerY + sin(angle) * (humanRadius + problemRadius + 6f)
            val problem = problemsBySlot[slot]
            if (problem != null) {
                drawProblem(canvas, px, py, problem, problemRadius)
            } else {
                // Пустой слот — рисуем полупрозрачную рамку
                val paint = Paint().apply {
                    color = Color.argb(40, 255, 255, 255)
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawCircle(px, py, problemRadius, paint)
            }
        }

        // Человек (в уменьшенном размере, по центру)
        humanBitmap?.let {
            val left = centerX - humanWidth / 2f
            val top = centerY - humanHeight / 2f
            canvas.drawBitmap(it, null, RectF(left, top, left + humanWidth, top + humanHeight), null)
        }

        // Внутренние метки — столбцом слева от человека
        val internalMarks = aura.marks.orEmpty().filter { it.external == 0 }.sortedBy { it.markId }
        val markSize = humanRadius * 0.18f
        val markGap = markSize * 0.13f
        for ((i, mark) in internalMarks.withIndex()) {
            val mx = centerX - humanRadius - markSize - 20f
            val my = centerY - (internalMarks.size - 1) * (markSize + markGap) / 2 + i * (markSize + markGap)
            drawMark(canvas, mx, my, mark, markSize)
        }

        // Внешние метки — столбцом справа от ауры
        val externalMarks = aura.marks.orEmpty().filter { it.external == 1 }.sortedBy { it.markId }
        for ((i, mark) in externalMarks.withIndex()) {
            val mx = centerX + auraRadius + 20f
            val my = centerY - (externalMarks.size - 1) * (markSize + markGap) / 2 + i * (markSize + markGap)
            drawMark(canvas, mx, my, mark, markSize)
        }
        canvas.restore()
    }

    private fun drawProblem(canvas: Canvas, x: Float, y: Float, problem: AuraProblem, radius: Float) {
        val resId = when (problem.problemType) {
            AuraProblemType.HOLE -> R.drawable.aura_problem_holl
            AuraProblemType.TEAR -> R.drawable.aura_problem_rift
            AuraProblemType.SCAR -> R.drawable.aura_problem_scar
            AuraProblemType.PARASITE -> R.drawable.aura_problem_parasite
            AuraProblemType.OTHER -> R.drawable.aura_problem_other
        }
        val bmp = try { BitmapFactory.decodeResource(resources, resId) } catch (e: Exception) { null }
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(x - radius, y - radius, x + radius, y + radius), null)
        } else {
            val paint = Paint().apply {
                color = Color.RED
                style = Paint.Style.FILL
                alpha = 180
            }
            canvas.drawCircle(x, y, radius, paint)
        }
    }

    private fun drawMark(canvas: Canvas, x: Float, y: Float, mark: AuraMark, size: Float) {
        val bmp = markBitmaps[mark.imageUrl]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(x, y, x + size, y + size), null)
        } else {
            val paint = Paint().apply { color = Color.LTGRAY; style = Paint.Style.FILL }
            canvas.drawRect(x, y, x + size, y + size, paint)
        }
        // Для клика — сохраняем область
        markTouchAreas.add(MarkTouchArea(RectF(x, y, x + size, y + size), mark))
    }

    // Цвет ауры по типу
    private fun auraColor(type: AuraType, percentOfHumanism: Int): Int {
        return when (type) {
            AuraType.DEMON -> Color.BLACK
            AuraType.ANGEL -> Color.WHITE
            AuraType.HUMAN -> Color.parseColor("#B0C4DE")
            AuraType.MAGE -> Color.parseColor("#4169E1")
            AuraType.CREATURE_OF_SPIRIT_WORLD -> Color.parseColor("#228B22")
            AuraType.CREATURE_OF_MYTH -> Color.parseColor("#FFD700")
            AuraType.CREATURE_OF_ABYSS -> Color.parseColor("#B22222")
            AuraType.CREATURE_OF_REALITY -> Color.parseColor("#8A2BE2")
            else -> Color.GRAY
        }
    }

    // Для обработки нажатий по меткам
    private data class MarkTouchArea(val rect: RectF, val mark: AuraMark)
    private val markTouchAreas = mutableListOf<MarkTouchArea>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.pointerCount) {
            1 -> handleDrag(event)
            2 -> handleZoom(event)
        }
        if (event.action == MotionEvent.ACTION_UP && event.pointerCount == 1) {
            // Проверяем клик по метке
            val x = (event.x - offsetX - width / 2f) / scaleFactor + width / 2f
            val y = (event.y - offsetY - height / 2f) / scaleFactor + height / 2f
            markTouchAreas.forEach {
                if (it.rect.contains(x, y)) {
                    Toast.makeText(context, it.mark.name, Toast.LENGTH_SHORT).show()
                    return true
                }
            }
        }
        return true
    }

    private fun handleDrag(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastTouchX = event.x
                lastTouchY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    offsetX += event.x - lastTouchX
                    offsetY += event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
            }
        }
    }

    private fun handleZoom(event: MotionEvent) {
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            lastScaleFactor = scaleFactor
            focusX = (event.getX(0) + event.getX(1)) / 2
            focusY = (event.getY(0) + event.getY(1)) / 2
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 2) {
            val dx = event.getX(0) - event.getX(1)
            val dy = event.getY(0) - event.getY(1)
            val distance = sqrt(dx * dx + dy * dy)
            val newScale = distance / 400f // 400 — базовое расстояние
            scaleFactor = (lastScaleFactor * newScale).coerceIn(0.5f, 3.5f)
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
    }
} 