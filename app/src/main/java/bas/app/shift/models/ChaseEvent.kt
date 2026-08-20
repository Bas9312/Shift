package bas.app.shift.models

/**
 * Что случилось с цепочкой погони, когда игрок вошёл в точку. Сервер присылает такие
 * события в ответе на отправку геолокации и на `POST /points/{id}/enter`, чтобы клиенту
 * не приходилось вычислять смену цели по разнице списков точек.
 */
data class ChaseEvent(
    /** started — цепочка начата; advanced — точка пройдена; finished — пройдена вся цепочка; dead_end — тупик. */
    val event: String,
    val questId: String? = null,
    val questName: String? = null,
    val pointId: String? = null,
    /** Текст точки (`textToShowOnEnter`), если он у неё есть. */
    val text: String? = null,
    /** Точки, которые теперь открыты игроку. Пусто — это конец ветки. */
    val next_point_ids: List<String>? = null,
    /** Куда возвращаться после тупика. */
    val start_point_id: String? = null,
) {
    companion object {
        const val STARTED = "started"
        const val ADVANCED = "advanced"
        const val FINISHED = "finished"
        const val DEAD_END = "dead_end"
    }
}

/** Ответ на `POST /users/location`. */
data class LocationUpdateResponse(
    val status: String,
    val chase: List<ChaseEvent>? = null,
)

/** Ответ на `POST /points/{id}/enter`. */
data class ChaseResponse(
    val status: String,
    val chase: List<ChaseEvent>? = null,
)

/**
 * Явное сообщение о входе в точку. Координаты нужны серверу, чтобы перепроверить вход:
 * доверять клиенту на слово нельзя, а последняя известная серверу позиция может отстать.
 */
data class EnterPointRequest(
    val playerId: String,
    val lat: Double,
    val lng: Double,
)
