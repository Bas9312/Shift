package bas.app.shift.ui

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.Path
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import bas.app.shift.R
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.*
import bas.app.shift.ui.AuraMarkCallback
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
    
    // Callback для long tap по меткам
    var markCallback: AuraMarkCallback? = null
    
    // Флаг для принудительного показа ауры (для МГ)
    // null = используем состояние с сервера, true = принудительно показать, false = принудительно скрыть
    private var forceAuraVisible: Boolean? = null
    
    // Флаг, что мы находимся в режиме редактора (только для МГ)
    private var isEditorMode = false

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
    private var isZooming = false // Флаг для отслеживания масштабирования
    private var lastPointerCount = 0 // Предыдущее количество пальцев
    private var initialDistance = 0f // Начальное расстояние между пальцами
    private var zoomEndTime = Long.MIN_VALUE // Время окончания масштабирования
    private val ZOOM_DELAY_MS = 100L // Задержка после масштабирования в миллисекундах
    
    // Long tap state
    private var longTapTimer: CountDownTimer? = null
    private var longTapMark: AuraMark? = null
    private var longTapProblemSlot: Int? = null
    private var longTapProblem: AuraProblem? = null
    private val LONG_TAP_DURATION = 500L // 500ms для long tap
    private val MOVEMENT_THRESHOLD = 20f // Порог движения в пикселях
    private var initialTouchX = 0f
    private var initialTouchY = 0f

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
    
    fun setAuraVisibility(visible: Boolean?) {
        forceAuraVisible = visible
        LogHelper.d("AuraCanvasView: setAuraVisibility: $visible, forceAuraVisible: $forceAuraVisible")
        invalidate()
    }
    
    fun setEditorMode(isEditor: Boolean) {
        isEditorMode = isEditor
        LogHelper.d("AuraCanvasView: setEditorMode: $isEditor")
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
        problemTouchAreas.clear()
        super.onDraw(canvas)
        aura?.let { drawAura(canvas, it) }
        
        // Логируем количество областей касаний для отладки
        //LogHelper.d("Touch areas - Marks: ${markTouchAreas.size}, Problems: ${problemTouchAreas.size}")
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
            // Определяем видимость: если forceAuraVisible не null, используем его, иначе серверное значение
            val isHidden = when (forceAuraVisible) {
                null -> aura.auraHidden      // Используем состояние с сервера
                true -> false                // Принудительно показать
                false -> true                // Принудительно скрыть
            }
            
            if (isHidden) {
                // Для скрытой ауры - однотонный серый цвет
                color = Color.GRAY
                alpha = 180
            } else {
                // Для видимой ауры - обычный цвет
                color = auraColor(aura.type, aura.percentOfHumanism)
                alpha = 180
            }
            style = Paint.Style.FILL
        }
        //LogHelper.d("Drawing aura: forceAuraVisible=$forceAuraVisible, auraHidden=${aura.auraHidden}, alpha=${paintAura.alpha}")
        canvas.drawCircle(centerX, centerY, auraRadius, paintAura)

        // 10 слотов для проблем по кругу вокруг человека (показываем только если аура не скрыта)
        val shouldShowElements = when (forceAuraVisible) {
            null -> !aura.auraHidden        // Используем состояние с сервера
            true -> true                    // Принудительно показать
            false -> false                  // Принудительно скрыть
        }
        if (shouldShowElements) {
            val slotsCount = 10
            val problemRadius = humanRadius * 0.13f
            val slotAngles = List(slotsCount) { i -> Math.toRadians((360.0 / slotsCount * i - 90.0)).toFloat() }
            val problemsBySlot = aura.auraProblems.orEmpty().associateBy { it.slot }
            for (slot in 0 until slotsCount) {
                val angle = slotAngles[slot]
                val px = centerX + cos(angle) * (auraRadius - problemRadius - 20f)
                val py = centerY + sin(angle) * (auraRadius - problemRadius - 20f)
                val problem = problemsBySlot[slot]
                if (problem != null) {
                    drawProblem(canvas, px, py, problem, problemRadius)
                } else {
                    // Пустой слот — рисуем более заметную рамку
                    val paint = Paint().apply {
                        color = Color.argb(80, 255, 255, 255)  // Увеличили альфу для лучшей видимости
                        style = Paint.Style.STROKE
                        strokeWidth = 3f  // Увеличили толщину линии
                    }
                    canvas.drawCircle(px, py, problemRadius, paint)
                }
                
                // Сохраняем область касания для проблем в экранных координатах
                val (screenPx1, screenPy1) = auraToScreenCoordinates(px - problemRadius, py - problemRadius)
                val (screenPx2, screenPy2) = auraToScreenCoordinates(px + problemRadius, py + problemRadius)
                val touchRect = RectF(screenPx1, screenPy1, screenPx2, screenPy2)
                problemTouchAreas.add(ProblemTouchArea(touchRect, slot, problem))
            }
        } else {
            // Если аура скрыта, очищаем области касаний для проблем
            problemTouchAreas.clear()
        }
        
        // Очищаем области касаний для меток если элементы скрыты
        if (!shouldShowElements) {
            markTouchAreas.clear()
        }

        // Человек (в уменьшенном размере, по центру) - показываем только если аура не скрыта
        if (shouldShowElements) {
            humanBitmap?.let {
                val left = centerX - humanWidth / 2f
                val top = centerY - humanHeight / 2f
                canvas.drawBitmap(it, null, RectF(left, top, left + humanWidth, top + humanHeight), null)
            }
        }

        // Внутренние метки — размещаем по типам (показываем только если аура не скрыта)
        if (shouldShowElements) {
            val internalMarks = aura.marks.orEmpty().filter { it.external == 0 }.sortedBy { it.markId }
            val markSize = humanRadius * 0.18f
            val markGap = markSize * 0.13f
            

            
            // Группируем метки по типам
            val magicDisciplineMarks = internalMarks.filter { it.markType == AuraMarkType.MAGIC_DISCIPLINE }
            val abilityMarks = internalMarks.filter { it.markType == AuraMarkType.ABILITY }
            val bottomMarks = internalMarks.filter { 
                it.markType in listOf(
                    AuraMarkType.JUDGE_STATUS, 
                    AuraMarkType.CONTRACT_BREACH, 
                    AuraMarkType.MARK_OF_CREATION, 
                    AuraMarkType.INSTRUMENT_LINK, 
                    AuraMarkType.SPIRITUAL_BEING_INSIDE,
                    AuraMarkType.MAGIC_LINK,
                    AuraMarkType.FOREIGN_PLANE_INFLUENCE
                )
            }
            val topMarks = internalMarks.filter { 
                it.markType in listOf(AuraMarkType.BLESSING, AuraMarkType.CURSE)
            }
            
            // Слева - магические дисциплины (внутри ауры)
            for ((i, mark) in magicDisciplineMarks.withIndex()) {
                val mx = centerX - (humanRadius + (auraRadius - humanRadius) * 0.2f) // Ближе к человеку
                val my = centerY - (magicDisciplineMarks.size - 1) * (markSize + markGap) / 2 + i * (markSize + markGap)
                drawMark(canvas, mx, my, mark, markSize)
            }
            
            // Справа - абилки (внутри ауры)
            for ((i, mark) in abilityMarks.withIndex()) {
                val mx = centerX + (humanRadius + (auraRadius - humanRadius) * 0.2f) // Ближе к человеку
                val my = centerY - (abilityMarks.size - 1) * (markSize + markGap) / 2 + i * (markSize + markGap)
                drawMark(canvas, mx, my, mark, markSize)
            }
            
            // Снизу - статусы, нарушения, метки творения, связи с инструментами, духовные существа (внутри ауры)
            for ((i, mark) in bottomMarks.withIndex()) {
                val spacing = markSize * 1.2f // Фиксированный отступ между метками
                val totalWidth = (bottomMarks.size - 1) * spacing
                val mx = centerX - totalWidth / 2 + i * spacing
                val my = centerY + (humanRadius + (auraRadius - humanRadius) * 0.15f) // Ближе к человеку
                drawMark(canvas, mx, my, mark, markSize)
            }
            
            // Сверху - благословения и проклятия (внутри ауры)
            for ((i, mark) in topMarks.withIndex()) {
                val spacing = markSize * 1.2f // Фиксированный отступ между метками
                val totalWidth = (topMarks.size - 1) * spacing
                val mx = centerX - totalWidth / 2 + i * spacing
                val my = centerY - (humanRadius + (auraRadius - humanRadius) * 0.20f) // Ближе к человеку
                

                
                drawMark(canvas, mx, my, mark, markSize)
            }

            // Внешние метки — столбцом справа от ауры (дальше от внутренних меток)
            val externalMarks = aura.marks.orEmpty().filter { it.external == 1 }.sortedBy { it.markId }
            for ((i, mark) in externalMarks.withIndex()) {
                val mx = centerX + auraRadius + 80f // Отодвигаем дальше от внутренних меток
                val my = centerY - (externalMarks.size - 1) * (markSize + markGap) / 2 + i * (markSize + markGap)
                drawMark(canvas, mx, my, mark, markSize)
            }
        } else {
            // Если аура скрыта, очищаем области касаний для меток
            markTouchAreas.clear()
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

    private fun drawMark(canvas: Canvas, centerX: Float, centerY: Float, mark: AuraMark, size: Float) {
        val x = centerX - size / 2f
        val y = centerY - size / 2f
        
        val bmp = markBitmaps[mark.imageUrl]
        if (bmp != null) {
            canvas.drawBitmap(bmp, null, RectF(x, y, x + size, y + size), null)
        } else {
            val paint = Paint().apply { 
                color = Color.LTGRAY
                style = Paint.Style.FILL
            }
            canvas.drawRect(x, y, x + size, y + size, paint)
        }
        
        // Отрисовываем звёздочки над меткой
        mark.numberOfStars?.let { stars ->
            if (stars > 0 && stars <= 5) {
                drawStarsAboveMark(canvas, x, y, size, stars)
            }
        }
        
        // Для клика — сохраняем область в экранных координатах
        val (screenX, screenY) = auraToScreenCoordinates(x, y)
        val (screenX2, screenY2) = auraToScreenCoordinates(x + size, y + size)
        markTouchAreas.add(MarkTouchArea(RectF(screenX, screenY, screenX2, screenY2), mark))
    }

    // Цвет ауры по типу
    private fun auraColor(type: AuraType, percentOfHumanism: Int): Int {
        return when (type) {
            AuraType.DEMON -> Color.BLACK
            AuraType.ANGEL -> Color.WHITE
            AuraType.HUMAN -> Color.parseColor("#B0C4DE")
            AuraType.MAGE -> {
                // Для мага цвет зависит от процента гуманизма
                if (percentOfHumanism == 100) {
                    // 100% гуманизм - стандартный цвет мага
                    Color.parseColor("#4169E1")
                } else if (percentOfHumanism == 0) {
                    // 0% гуманизм - цвет как у духовного существа
                    Color.parseColor("#228B22")
                } else {
                    // Промежуточные значения - интерполяция между цветами
                    interpolateColor(
                        Color.parseColor("#228B22"), // Духовное существо (0% гуманизм)
                        Color.parseColor("#4169E1"), // Маг (100% гуманизм)
                        percentOfHumanism / 100f
                    )
                }
            }
            AuraType.CREATURE_OF_SPIRIT_WORLD -> Color.parseColor("#228B22")
            AuraType.CREATURE_OF_MYTH -> Color.parseColor("#FFD700")
            AuraType.CREATURE_OF_ABYSS -> Color.parseColor("#B22222")
            AuraType.CREATURE_OF_REALITY -> Color.parseColor("#8A2BE2")
            else -> Color.GRAY
        }
    }
    
    // Функция интерполяции между двумя цветами
    private fun interpolateColor(color1: Int, color2: Int, ratio: Float): Int {
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)
        
        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)
        
        val r = (r1 + (r2 - r1) * ratio).toInt()
        val g = (g1 + (g2 - g1) * ratio).toInt()
        val b = (b1 + (b2 - b1) * ratio).toInt()
        
        return Color.rgb(r, g, b)
    }

    // Для обработки нажатий по меткам
    private data class MarkTouchArea(val rect: RectF, val mark: AuraMark)
    private val markTouchAreas = mutableListOf<MarkTouchArea>()
    
    // Для обработки нажатий по проблемам
    private data class ProblemTouchArea(val rect: RectF, val slot: Int, val problem: AuraProblem?)
    private val problemTouchAreas = mutableListOf<ProblemTouchArea>()
    
    // Функция для преобразования координат ауры в экранные координаты
    private fun auraToScreenCoordinates(auraX: Float, auraY: Float): Pair<Float, Float> {
        val screenX = (auraX - width / 2f) * scaleFactor + width / 2f + offsetX
        val screenY = (auraY - height / 2f) * scaleFactor + height / 2f + offsetY
        return Pair(screenX, screenY)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentPointerCount = event.pointerCount
        
        // Отслеживаем переход с 2 пальцев на 1 или 0
        if (lastPointerCount == 2 && currentPointerCount < 2) {
            // Завершили масштабирование
            isZooming = false
            zoomEndTime = System.currentTimeMillis()
            initialDistance = 0f
            //LogHelper.d("Zoom ended, setting zoomEndTime: $zoomEndTime")
        }
        
        // Обновляем количество пальцев
        lastPointerCount = currentPointerCount
        
        when (currentPointerCount) {
            1 -> {
                // Проверяем, прошло ли достаточно времени после масштабирования
                val timeSinceZoom = System.currentTimeMillis() - zoomEndTime
                val isInZoomDelay = zoomEndTime != Long.MIN_VALUE && timeSinceZoom < ZOOM_DELAY_MS
                
                //LogHelper.d("Touch check: isZooming=$isZooming, zoomEndTime=$zoomEndTime, timeSinceZoom=${timeSinceZoom}ms, isInZoomDelay=$isInZoomDelay")
                
                // Блокируем перетаскивание, если только что закончили масштабирование или в период задержки
                if (!isZooming && !isInZoomDelay) {
                    handleDrag(event)
                } else if (isInZoomDelay) {
                    //LogHelper.d("Blocking drag due to zoom delay: ${timeSinceZoom}ms")
                }
            }
            2 -> {
                isZooming = true // Устанавливаем флаг масштабирования
                handleZoom(event)
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
                initialTouchX = event.x
                initialTouchY = event.y
                
                // Проверяем, есть ли метка под пальцем
                //LogHelper.d("Touch at screen: (${event.x}, ${event.y}), scale: $scaleFactor, offset: ($offsetX, $offsetY)")
                
                longTapMark = markTouchAreas.find { it.rect.contains(event.x, event.y) }?.mark
                
                // Проверяем, есть ли проблема под пальцем
                val problemTouchArea = problemTouchAreas.find { it.rect.contains(event.x, event.y) }
                if (problemTouchArea != null) {
                    longTapProblemSlot = problemTouchArea.slot
                    longTapProblem = problemTouchArea.problem
                }
                
                // Запускаем таймер для long tap
                if (longTapMark != null || longTapProblemSlot != null) {
                    startLongTapTimer()
                    //LogHelper.d("Long tap timer started for mark: ${longTapMark != null}, problem: ${longTapProblemSlot != null}")
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // Проверяем, не находимся ли мы в периоде задержки после масштабирования
                val timeSinceZoom = System.currentTimeMillis() - zoomEndTime
                val isInZoomDelay = zoomEndTime != Long.MIN_VALUE && timeSinceZoom < ZOOM_DELAY_MS
                
                //LogHelper.d("Drag move check: isDragging=$isDragging, isZooming=$isZooming, timeSinceZoom=${timeSinceZoom}ms, isInZoomDelay=$isInZoomDelay")
                
                if (isDragging && !isZooming && !isInZoomDelay) {
                    // Проверяем, превышен ли порог движения
                    val movementDistance = sqrt(
                        (event.x - initialTouchX) * (event.x - initialTouchX) + 
                        (event.y - initialTouchY) * (event.y - initialTouchY)
                    )
                    
                    if (movementDistance > MOVEMENT_THRESHOLD) {
                        // Если движение значительное, отменяем long tap
                        //LogHelper.d("Significant finger movement ($movementDistance), canceling long tap timer")
                        cancelLongTapTimer()
                    }
                    
                    offsetX += event.x - lastTouchX
                    offsetY += event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                } else if (isInZoomDelay) {
                    //LogHelper.d("Blocking drag movement due to zoom delay: ${timeSinceZoom}ms")
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                cancelLongTapTimer()
                // Сбрасываем переменные long tap при отпускании пальца
                longTapMark = null
                longTapProblemSlot = null
                longTapProblem = null
            }
        }
    }

    private fun handleZoom(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Сохраняем текущий масштаб и начальное расстояние
                lastScaleFactor = scaleFactor
                focusX = (event.getX(0) + event.getX(1)) / 2
                focusY = (event.getY(0) + event.getY(1)) / 2
                
                // Вычисляем и сохраняем начальное расстояние между пальцами
                val dx = event.getX(0) - event.getX(1)
                val dy = event.getY(0) - event.getY(1)
                initialDistance = sqrt(dx * dx + dy * dy)
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2) {
                    // Вычисляем текущее расстояние между пальцами
                    val dx = event.getX(0) - event.getX(1)
                    val dy = event.getY(0) - event.getY(1)
                    val currentDistance = sqrt(dx * dx + dy * dy)
                    
                    // Вычисляем новый масштаб относительно начального расстояния
                    if (initialDistance > 0) {
                        val scaleRatio = currentDistance / initialDistance
                        val newScaleFactor = (lastScaleFactor * scaleRatio).coerceIn(0.5f, 3.5f)
                        
                        // Простое масштабирование без сложной корректировки смещения
                        scaleFactor = newScaleFactor
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // Логика окончания масштабирования теперь в onTouchEvent
            }
        }
    }

    private fun startLongTapTimer() {
        cancelLongTapTimer()
        //LogHelper.d("Starting long tap timer")
        longTapTimer = object : CountDownTimer(LONG_TAP_DURATION, LONG_TAP_DURATION) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                //LogHelper.d("Long tap timer finished, mark: ${longTapMark != null}, problem: ${longTapProblemSlot != null}")
                longTapMark?.let { mark ->
                    // Все метки работают одинаково в любом режиме
                    LogHelper.d("Calling onMarkLongTap for mark: ${mark.markId}")
                    markCallback?.onMarkLongTap(mark)
                }
                longTapProblemSlot?.let { slot ->
                    LogHelper.d("Calling onProblemLongTap for slot: $slot")
                    markCallback?.onProblemLongTap(slot, longTapProblem)
                }
            }
        }.start()
    }
    
    private fun cancelLongTapTimer() {
        longTapTimer?.cancel()
        longTapTimer = null
        // НЕ сбрасываем longTapMark и longTapProblem здесь, 
        // они должны сохраняться до ACTION_UP
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        cancelLongTapTimer()
    }
    
    // Отрисовка звёздочек над меткой
    private fun drawStarsAboveMark(canvas: Canvas, markX: Float, markY: Float, markSize: Float, numberOfStars: Int) {
        val starSize = markSize * 0.15f  // Размер звёздочки относительно метки
        val starSpacing = starSize * 0.8f  // Расстояние между звёздочками
        val totalWidth = (numberOfStars - 1) * starSpacing  // Общая ширина всех звёздочек
        val startX = markX + markSize / 2 - totalWidth / 2  // Начальная позиция по X
        val starY = markY - starSize * 1.2f  // Позиция по Y (над меткой)
        
        // Рисуем каждую звёздочку
        for (i in 0 until numberOfStars) {
            val starX = startX + i * starSpacing
            drawStar(canvas, starX, starY, starSize)
        }
    }
    
    // Отрисовка одной звёздочки
    private fun drawStar(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val outerRadius = size
        val innerRadius = size * 0.4f
        val points = 5  // 5-конечная звезда
        
        val path = Path()
        val angleStep = (2 * Math.PI) / points
        
        for (i in 0 until points * 2) {
            val angle = i * angleStep / 2
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            val x = centerX + cos(angle).toFloat() * radius
            val y = centerY + sin(angle).toFloat() * radius
            
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        
        // Рисуем тень звёздочки (смещена вниз и вправо)
        val shadowPath = Path()
        shadowPath.offset(size * 0.1f, size * 0.1f)
        shadowPath.addPath(path)
        
        val shadowPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
            alpha = 80
        }
        canvas.drawPath(shadowPath, shadowPaint)
        
        // Рисуем основную звёздочку
        val starPaint = Paint().apply {
            color = Color.YELLOW
            style = Paint.Style.FILL
            alpha = 255
        }
        canvas.drawPath(path, starPaint)
        
        // Добавляем блик (белая линия по краю)
        val highlightPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = size * 0.1f
            alpha = 180
        }
        canvas.drawPath(path, highlightPaint)
    }
} 