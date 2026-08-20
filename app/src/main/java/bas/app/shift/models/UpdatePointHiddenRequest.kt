package bas.app.shift.models

/**
 * Тело `PATCH /api/v1/points/{id}`. Сервер обновляет только те поля, что реально пришли,
 * а Gson по умолчанию не сериализует null — поэтому неуказанные поля остаются нетронутыми.
 */
data class UpdatePointRequest(
    val hidden: Boolean? = null,
    val trackable: Boolean? = null,
    /** Пустая строка — «стереть ауру», null — «не трогать». */
    val aura_text: String? = null,
    /** Следующая точка цепочки погони. Пустая строка — «убрать переход», null — «не трогать». */
    val next_point_id: String? = null,
)

/** Тело `POST /api/v1/points/{id}/bind` — занять фамильяра под себя. */
data class BindFamiliarRequest(
    val playerId: String,
)
