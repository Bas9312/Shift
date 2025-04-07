package bas.app.shift.models

import com.google.android.gms.maps.model.LatLng

enum class PointType {
    FAMILIAR,                    // Фамильяры
    FAKE_FAMILIAR,              // За жопу кусаки которые косят под фамильяров
    OPEN_PROBLEM,               // Открытые точки с локальными проблемами
    AGGRESSIVE_FAMILIAR,        // За жопу кусаки которые идут к вам
    HIDDEN_EFFECT,              // Скрытые точки с эффектами
    SHRINKING_CIRCLE            // Сужающийся круг после ритуала
}

data class PointOfInterest(
    val id: String,
    val position: LatLng,        // Реальная позиция точки
    val virtualCenter: LatLng,   // Виртуальный центр круга
    val radius: Float,           // в метрах
    val type: PointType,
    val additionalInfo: Map<String, Any> = emptyMap()
) 