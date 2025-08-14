package bas.app.shift.ui

import bas.app.shift.models.AuraMark
import bas.app.shift.models.AuraProblem

interface AuraMarkCallback {
    fun onMarkLongTap(mark: AuraMark)
    fun onProblemLongTap(slot: Int, problem: AuraProblem?)
}
