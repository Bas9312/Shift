package bas.app.shift.models

data class Point(
    val pointId: String,
    val type: String,
    val lat: Double,
    val lng: Double,
    val vLat: Double? = null,
    val vLng: Double? = null,
    val radius: Double,
    val initial_radius: Double? = null,
    val expireAt: String? = null,
    val ownerId: String? = null,
    val description: String? = null,
    val textToShowOnEnter: String? = null,
    val createdAt: String? = null,
    val aura_text: String? = null,
    val next_point_id: String? = null,
    val trackable: Int? = null,
    val hidden: Int? = null,
    /**
     * Видна ли булавка точки издалека. 0 (и отсутствие поля) — прежнее поведение: издалека
     * игрок видит только круг, маркер появляется при входе в радиус. 1 — маркер виден всегда.
     * Решает мастер в панели МГ; приложение своих правил отображения больше не выдумывает.
     */
    val marker_from_afar: Int? = null,
    /** Кто сейчас общается с фамильяром. Сервер снимает привязку сам через 15 минут молчания. */
    val assigned_player: String? = null,
    val last_message_time: String? = null,
)

val Point.vLatOrLat: Double
    get() = vLat ?: lat

val Point.vLngOrLng: Double
    get() = vLng ?: lng