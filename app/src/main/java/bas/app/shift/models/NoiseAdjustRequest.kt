package bas.app.shift.models

data class NoiseAdjustRequest(
    val delta: Double                   // +увеличение / -уменьшение локального шума
)
