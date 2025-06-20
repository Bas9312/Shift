package bas.app.shift.models

data class Aura(
    val entityId: String,
    val type: AuraType,
    val percentOfHumanism: Int,
    val auraHidden: Boolean,
    val auraProblems: List<AuraProblem>,
    val marks: List<AuraMark>
) 