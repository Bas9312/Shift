package bas.app.shift.models

import com.google.gson.annotations.SerializedName

/**
 * Цена команды терминала в шуме. Приходит с сервера (`GET /noize_api/api/v1/commands`),
 * чтобы баланс правился из панели мастера, а не пересборкой приложения.
 */
data class CommandCost(
    val command: String,
    val cost: Double,
    /** true — цену задаёт мастер на месте (глубина у DEEP_DIVE.END), справочник её не диктует. */
    @SerializedName("allow_client_value") val allowClientValue: Boolean = false,
)

data class CommandCostsResponse(
    val commands: List<CommandCost>
)
