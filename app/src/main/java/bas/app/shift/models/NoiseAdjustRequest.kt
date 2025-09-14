package bas.app.shift.models

data class NoiseAdjustRequest(
    val delta: Int,                      // +увеличение / -уменьшение локального шума
    val reason: String? = null
)
