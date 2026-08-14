package bas.app.shift.helpers

import bas.app.shift.models.AuraProblemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuraCleanupManagerTest {

    @Test
    fun canClean_onlyTearAndScarAreCleanable() {
        assertTrue(AuraCleanupManager.canClean(AuraProblemType.TEAR))
        assertTrue(AuraCleanupManager.canClean(AuraProblemType.SCAR))
        assertFalse(AuraCleanupManager.canClean(AuraProblemType.HOLE))
        assertFalse(AuraCleanupManager.canClean(AuraProblemType.PARASITE))
        assertFalse(AuraCleanupManager.canClean(AuraProblemType.OTHER))
    }

    @Test
    fun durationMinutes_matchesDesignedTimings() {
        assertEquals(15, AuraCleanupManager.durationMinutes(AuraProblemType.TEAR))
        assertEquals(5, AuraCleanupManager.durationMinutes(AuraProblemType.SCAR))
        assertNull(AuraCleanupManager.durationMinutes(AuraProblemType.HOLE))
    }

    @Test
    fun outcomeFor_tearConvertsToScar() {
        val outcome = AuraCleanupManager.outcomeFor(AuraProblemType.TEAR)
        assertTrue(outcome is AuraCleanupManager.Outcome.Converted)
        assertEquals(AuraProblemType.SCAR, (outcome as AuraCleanupManager.Outcome.Converted).toType)
    }

    @Test
    fun outcomeFor_scarIsFullyRemoved() {
        assertEquals(AuraCleanupManager.Outcome.Removed, AuraCleanupManager.outcomeFor(AuraProblemType.SCAR))
    }

    @Test
    fun outcomeFor_nonCleanableTypes_returnNull() {
        assertNull(AuraCleanupManager.outcomeFor(AuraProblemType.HOLE))
        assertNull(AuraCleanupManager.outcomeFor(AuraProblemType.PARASITE))
        assertNull(AuraCleanupManager.outcomeFor(AuraProblemType.OTHER))
    }

    @Test
    fun progress_remainingMs_countsDownToZeroAndClampsAtZero() {
        val startedAt = 1_000_000L
        val progress = AuraCleanupManager.Progress(startedAt, AuraProblemType.SCAR) // 5 минут
        val durationMs = 5 * 60_000L

        assertEquals(durationMs, progress.remainingMs(startedAt))
        assertEquals(durationMs / 2, progress.remainingMs(startedAt + durationMs / 2))
        assertEquals(0L, progress.remainingMs(startedAt + durationMs))
        // Время не должно уходить в минус, если проверка идёт много позже готовности
        // (например, экран открыли на следующий день).
        assertEquals(0L, progress.remainingMs(startedAt + durationMs + 999_999L))
    }

    @Test
    fun progress_isReady_falseBeforeDeadlineTrueAtAndAfter() {
        val startedAt = 0L
        val progress = AuraCleanupManager.Progress(startedAt, AuraProblemType.TEAR) // 15 минут
        val durationMs = 15 * 60_000L

        assertFalse(progress.isReady(durationMs - 1))
        assertTrue(progress.isReady(durationMs))
        assertTrue(progress.isReady(durationMs + 1))
    }
}
