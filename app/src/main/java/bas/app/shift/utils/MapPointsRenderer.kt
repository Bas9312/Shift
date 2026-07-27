package bas.app.shift.utils

import android.location.Location
import bas.app.shift.helpers.DateTimeHelper
import bas.app.shift.models.Point
import bas.app.shift.models.PointType
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Circle
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Владеет отрисованными на карте точками (`pointsOfInterest`) и маркером геолокации игрока —
 * вынесено из EkatMaps, чтобы god-класс не совмещал ещё и хранение/диффинг состояния карты.
 * Диффит, а не пересоздаёт: существующие круги/маркеры двигаются на месте, что убирает
 * мерцание и сброс открытого info-window при периодическом (раз в 10с) обновлении.
 */
class MapPointsRenderer(
    private val map: GoogleMap,
    private val isMgUser: Boolean
) {
    private val pointsOfInterest = mutableMapOf<String, Triple<Point, Circle?, Marker?>>()
    private var currentLocationMarker: Marker? = null

    /** true после первого refreshMarkersForLocation — вызывающий код центрирует камеру только один раз. */
    val hasLocationMarker: Boolean
        get() = currentLocationMarker != null

    /** Диффит серверный список с уже отрисованным: убирает лишнее, добавляет/двигает остальное. */
    fun syncPoints(serverPoints: List<Point>) {
        val desired = serverPoints.filter { point ->
            when {
                !isMgUser && point.type == "POINT_WITH_TEXT" -> false
                !isMgUser && point.hidden == 1 -> false
                else -> true
            }
        }
        val desiredIds = desired.map { it.pointId }.toSet()

        // Удаляем то, чего больше нет (или что стало скрытым/отфильтрованным)
        val toRemove = pointsOfInterest.keys.filter { it !in desiredIds }
        toRemove.forEach { id ->
            pointsOfInterest[id]?.let { (_, circle, marker) ->
                circle?.remove()
                marker?.remove()
            }
            pointsOfInterest.remove(id)
        }

        // Добавляем новые и обновляем существующие на месте
        desired.forEach { upsertPoint(it) }
    }

    /** Двигает маркер геолокации игрока и показывает/прячет маркеры точек по видимости/радиусу. */
    fun refreshMarkersForLocation(location: Location) {
        val latLng = LatLng(location.latitude, location.longitude)

        // Двигаем маркер геолокации на месте (раньше он пересоздавался на каждый апдейт → мерцание синей метки)
        if (currentLocationMarker == null) {
            currentLocationMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Ваше местоположение")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            currentLocationMarker?.position = latLng
        }

        // Для MG пользователей показываем все точки всегда
        if (isMgUser) {
            pointsOfInterest.forEach { (id, pointData) ->
                val (point, circle, currentMarker) = pointData

                // Пропускаем точки типа USER (у них нет кругов и они не нужны на карте)
                if (point.type == "USER" && !isMgUser) {
                    return@forEach
                }

                // Если маркера еще нет - создаем его
                if (currentMarker == null) {
                    val newMarker = map.addMarker(
                        PointVisualizer.getMarkerOptions(
                            LatLng(point.lat, point.lng),
                            PointType.fromServerValue(point.type),
                            getPointTitle(PointType.fromServerValue(point.type)),
                            getPointDescription(point)
                        )
                    )
                    pointsOfInterest[id] = Triple(point, circle, newMarker)
                }
            }
        } else {
            // Для обычных пользователей проверяем, находится ли пользователь в каких-либо кругах
            pointsOfInterest.forEach { (id, pointData) ->
                val (point, circle, currentMarker) = pointData

                val virtualCenter = LatLng(point.vLat ?: point.lat, point.vLng ?: point.lng)
                val distance = if (point.type == "USER") 0f else calculateDistance(latLng, virtualCenter)

                if (distance <= point.radius) {
                    // Если пользователь в круге и маркера еще нет - создаем его
                    if (currentMarker == null) {
                        val newMarker = map.addMarker(
                            PointVisualizer.getMarkerOptions(
                                LatLng(point.lat, point.lng),
                                PointType.fromServerValue(point.type),
                                getPointTitle(PointType.fromServerValue(point.type)),
                                getPointDescription(point)
                            )
                        )
                        pointsOfInterest[id] = Triple(point, circle, newMarker)
                    }
                } else {
                    // Если пользователь вне круга и маркер существует - удаляем его
                    if (currentMarker != null) {
                        currentMarker.remove()
                        pointsOfInterest[id] = Triple(point, circle, null)
                    }
                }
            }
        }
    }

    fun findPointForMarker(marker: Marker): Point? =
        pointsOfInterest.values.find { (_, _, markerRef) -> markerRef == marker }?.first

    /** Снимок игроков (точки типа USER) для диалога поиска игрока (кнопка МГ). */
    fun usersSnapshot(): List<Point> =
        pointsOfInterest.values.map { it.first }.filter { it.type == "USER" }

    fun getPointTitle(type: PointType): String {
        return when (type) {
            PointType.USER -> "Кто-то в игре"
            PointType.FAMILIAR -> "Фамильяр"
            PointType.HIDDEN_EFFECT_AREA -> "Скрытая зона эффекта"
            PointType.FAKE_FAMILIAR_BITER -> "'Фамильяр'"
            PointType.APPROACHING_BITER -> "Приближающийся `Фамильяр`"
            PointType.OPEN_PROBLEM -> "Открытая Проблема"
            PointType.SHRINKING_CIRCLE -> "Сужающийся Круг"
            PointType.DEMON_BLACK_CIRCLE -> "Демон Черный Круг"
            PointType.APPROACHING_VIRTUAL -> "Приближающаяся Виртуальная проблема"
            PointType.HIDDEN_AR_POINT -> "Скрытая AR точка"
            PointType.POINT_WITH_TEXT -> "Точка с текстом"
            PointType.UNKNOWN -> "Неизвестный тип точки"
        }
    }

    fun getPointDescription(point: Point): String {
        return when (PointType.fromServerValue(point.type)) {
            PointType.SHRINKING_CIRCLE -> {
                val expireText = DateTimeHelper.formatExpireAt(point.expireAt)
                if (expireText != null) {
                    "Радиус: ${point.radius}м\nИстекает: $expireText"
                } else {
                    "Радиус: ${point.radius}м"
                }
            }
            else -> "Радиус: ${point.radius}м"
        }
    }

    fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    private fun addPoint(point: Point) {
        // Для обычных пользователей: не показываем точки типа POINT_WITH_TEXT
        if (!isMgUser && point.type == "POINT_WITH_TEXT") {
            return
        }

        // Для обычных пользователей: скрытые точки не показываем на карте
        if (!isMgUser && point.hidden == 1) {
            return
        }

        // Удаляем старую точку, если она существует
        pointsOfInterest[point.pointId]?.let { (_, circle, marker) ->
            circle?.remove() // Круг может быть null для USER точек
            marker?.remove()
        }
        pointsOfInterest.remove(point.pointId)

        // Для точек типа USER не создаем круги
        val circle = if (point.type == "USER") {
            null
        } else {
            map.addCircle(
                PointVisualizer.getCircleOptions(
                    LatLng(point.vLat ?: point.lat, point.vLng ?: point.lng),
                    point.radius.toFloat(),
                    PointType.fromServerValue(point.type)
                )
            )
        }

        // Сохраняем точку, круг и null для маркера (он будет добавлен позже, в refreshMarkersForLocation)
        pointsOfInterest[point.pointId] = Triple(point, circle, null)
    }

    /**
     * Добавляет новую точку или обновляет существующую БЕЗ пересоздания:
     * двигает уже созданные круг и маркер на месте.
     */
    private fun upsertPoint(point: Point) {
        val existing = pointsOfInterest[point.pointId]
        if (existing == null) {
            addPoint(point)
            return
        }
        val (oldPoint, circle, marker) = existing
        // Смена типа влияет на цвет круга и иконку маркера — тут проще пересоздать
        if (oldPoint.type != point.type) {
            circle?.remove()
            marker?.remove()
            pointsOfInterest.remove(point.pointId)
            addPoint(point)
            return
        }
        // Двигаем существующие круг и маркер на новые координаты
        val virtualCenter = LatLng(point.vLat ?: point.lat, point.vLng ?: point.lng)
        circle?.let {
            it.center = virtualCenter
            it.radius = point.radius
        }
        marker?.let { it.position = LatLng(point.lat, point.lng) }
        pointsOfInterest[point.pointId] = Triple(point, circle, marker)
    }
}
