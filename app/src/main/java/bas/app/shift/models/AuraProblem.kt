package bas.app.shift.models

import com.google.gson.annotations.SerializedName
import java.time.LocalDateTime

// slot: 0-9
// problem_type: enum
// name: string
// description: string? (optional)
// created_at: string (ISO-8601)
data class AuraProblem(
    val slot: Int,
    @SerializedName("problem_type")
    val problemType: AuraProblemType,
    val name: String,
    val description: String? = null,
    @SerializedName("created_at")
    val createdAt: String // ISO-8601
) 