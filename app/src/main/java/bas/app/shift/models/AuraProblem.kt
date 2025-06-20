package bas.app.shift.models

import java.time.LocalDateTime

// slot: 0-9
// problem_type: enum
// name: string
// description: string? (optional)
// created_at: string (ISO-8601)
data class AuraProblem(
    val slot: Int,
    val problemType: AuraProblemType,
    val name: String,
    val description: String? = null,
    val createdAt: String // ISO-8601
) 