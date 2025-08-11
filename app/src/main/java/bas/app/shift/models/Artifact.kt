package bas.app.shift.models

import com.google.gson.annotations.SerializedName

data class Artifact(
    val id: Int,
    val name: String,
    val level: String,
    val type: String,
    @SerializedName("creator_user_id") val creatorUserId: String,
    @SerializedName("creator_name") val creatorName: String,
    val material: String,
    val properties: String
)

data class ArtifactRequest(
    val name: String,
    val level: String,
    val type: String,
    @SerializedName("creator_user_id") val creatorUserId: String,
    val material: String,
    val properties: String
) 