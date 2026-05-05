package bas.app.shift.models

data class ApiInfoResponse(
    val version: String? = null,
    val endpoints: List<String>? = null,
    val notes: String? = null,
)

