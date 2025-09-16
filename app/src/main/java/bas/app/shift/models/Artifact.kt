package bas.app.shift.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Artifact(
    val id: Int,
    val name: String,
    val level: String,
    val type: String,
    @SerializedName("creator_user_id") val creatorUserId: String,
    @SerializedName("creator_name") val creatorName: String,
    @SerializedName("binding_to_name") val bindingToName: String?,
    val material: String,
    val properties: String
) : Serializable

data class ArtifactRequest(
    val name: String,
    val level: String,
    val type: String,
    @SerializedName("creator_user_id") val creatorUserId: String,
    @SerializedName("binding_to_name") val bindingToName: String?,
    val material: String,
    val properties: String
)

data class ArtifactUpdateRequest(
    @SerializedName("binding_to_name") val bindingToName: String
) 