package bas.app.shift.helpers

import bas.app.shift.models.Ability
import bas.app.shift.models.AuraType
import bas.app.shift.models.Effect
import bas.app.shift.models.NamedEntity
import bas.app.shift.models.ShortArtifact
import bas.app.shift.models.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDifferTest {

    private fun baseUser(
        disciplines: List<NamedEntity> = emptyList(),
        modules: List<NamedEntity> = emptyList(),
        abilities: List<Ability> = emptyList(),
        artifacts: List<ShortArtifact> = emptyList(),
        instrument: String? = null,
        familiar: String? = null,
        misc: List<String> = emptyList(),
        playerName: String = "Bas",
        characterName: String = "Ворон",
        effects: List<Effect>? = emptyList()
    ) = User(
        userId = "u1",
        playerName = playerName,
        characterName = characterName,
        disciplines = disciplines,
        modules = modules,
        abilities = abilities,
        artifacts = artifacts,
        instrument = instrument,
        familiar = familiar,
        misc = misc,
        type = AuraType.HUMAN,
        effects = effects
    )

    @Test
    fun diff_identicalProfiles_returnsNoChanges() {
        val user = baseUser()
        assertEquals(emptyList<ProfileChange>(), ProfileDiffer.diff(user, user))
    }

    @Test
    fun diff_disciplineAdded() {
        val old = baseUser(disciplines = emptyList())
        val new = baseUser(disciplines = listOf(NamedEntity(1, "Экстрасенсорика")))
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Дисциплина", changes[0].fieldName)
        assertEquals(ChangeType.ADDED, changes[0].changeType)
        assertEquals("Экстрасенсорика", changes[0].newValue)
    }

    @Test
    fun diff_disciplineRemoved() {
        val old = baseUser(disciplines = listOf(NamedEntity(1, "Экстрасенсорика")))
        val new = baseUser(disciplines = emptyList())
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals(ChangeType.REMOVED, changes[0].changeType)
        assertEquals("Экстрасенсорика", changes[0].oldValue)
    }

    @Test
    fun diff_moduleAddedAndRemoved_bothReported() {
        val old = baseUser(modules = listOf(NamedEntity(1, "Модуль А")))
        val new = baseUser(modules = listOf(NamedEntity(2, "Модуль Б")))
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(2, changes.size)
        assertTrue(changes.any { it.changeType == ChangeType.ADDED && it.newValue == "Модуль Б" })
        assertTrue(changes.any { it.changeType == ChangeType.REMOVED && it.oldValue == "Модуль А" })
    }

    @Test
    fun diff_abilityAdded() {
        val old = baseUser(abilities = emptyList())
        val new = baseUser(abilities = listOf(Ability(1, "Тип", "Описание")))
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Способность", changes[0].fieldName)
        assertEquals(ChangeType.ADDED, changes[0].changeType)
    }

    @Test
    fun diff_artifactAdded() {
        val old = baseUser(artifacts = emptyList())
        val new = baseUser(artifacts = listOf(ShortArtifact(1, "Амулет")))
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Артефакт", changes[0].fieldName)
        assertEquals("Амулет", changes[0].newValue)
    }

    @Test
    fun diff_instrumentChanged() {
        val old = baseUser(instrument = "Карты")
        val new = baseUser(instrument = "Маятник")
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Инструмент", changes[0].fieldName)
        assertEquals(ChangeType.CHANGED, changes[0].changeType)
        assertEquals("Карты", changes[0].oldValue)
        assertEquals("Маятник", changes[0].newValue)
    }

    @Test
    fun diff_familiarChanged() {
        val old = baseUser(familiar = "Кот")
        val new = baseUser(familiar = "Ворон")
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Фамильяр", changes[0].fieldName)
    }

    @Test
    fun diff_miscAddedAndRemoved() {
        val old = baseUser(misc = listOf("Старое"))
        val new = baseUser(misc = listOf("Новое"))
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(2, changes.size)
        assertTrue(changes.any { it.changeType == ChangeType.ADDED && it.newValue == "Новое" })
        assertTrue(changes.any { it.changeType == ChangeType.REMOVED && it.oldValue == "Старое" })
    }

    @Test
    fun diff_playerNameChanged() {
        val old = baseUser(playerName = "Bas")
        val new = baseUser(playerName = "MG_Bas")
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Имя игрока", changes[0].fieldName)
    }

    @Test
    fun diff_characterNameChanged() {
        val old = baseUser(characterName = "Ворон")
        val new = baseUser(characterName = "Сова")
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Имя персонажа", changes[0].fieldName)
    }

    @Test
    fun diff_effectAdded() {
        val old = baseUser(effects = emptyList())
        val new = baseUser(effects = listOf(Effect(1, "Слабость", 10, null)))
        val changes = ProfileDiffer.diff(old, new)
        assertEquals(1, changes.size)
        assertEquals("Эффект", changes[0].fieldName)
        assertEquals(ChangeType.ADDED, changes[0].changeType)
        assertEquals("Слабость", changes[0].newValue)
    }

    @Test
    fun diff_effectsNullOnBothSides_noChange() {
        val old = baseUser(effects = null)
        val new = baseUser(effects = null)
        assertEquals(emptyList<ProfileChange>(), ProfileDiffer.diff(old, new))
    }

    @Test
    fun diff_effectsNullToEmpty_noChange() {
        // null и emptyList должны считаться эквивалентными списками эффектов.
        val old = baseUser(effects = null)
        val new = baseUser(effects = emptyList())
        assertEquals(emptyList<ProfileChange>(), ProfileDiffer.diff(old, new))
    }

    @Test
    fun diff_reorderingOnly_noChangeReported() {
        // Сортировка перед сравнением означает, что порядок в списке не важен.
        val old = baseUser(disciplines = listOf(NamedEntity(1, "А"), NamedEntity(2, "Б")))
        val new = baseUser(disciplines = listOf(NamedEntity(2, "Б"), NamedEntity(1, "А")))
        assertEquals(emptyList<ProfileChange>(), ProfileDiffer.diff(old, new))
    }

    @Test
    fun formatMessage_added_regularField() {
        val change = ProfileChange("Модуль", null, "Модуль Б", ChangeType.ADDED)
        assertEquals("Модуль добавлен: Модуль Б", ProfileDiffer.formatMessage(change))
    }

    @Test
    fun formatMessage_added_effect_usesSpecialWording() {
        val change = ProfileChange("Эффект", null, "Слабость", ChangeType.ADDED)
        assertEquals("Получен новый эффект: Слабость", ProfileDiffer.formatMessage(change))
    }

    @Test
    fun formatMessage_removed_effect_usesSpecialWording() {
        val change = ProfileChange("Эффект", "Слабость", null, ChangeType.REMOVED)
        assertEquals("Эффект исчез: Слабость", ProfileDiffer.formatMessage(change))
    }

    @Test
    fun formatMessage_removed_regularField() {
        val change = ProfileChange("Артефакт", "Амулет", null, ChangeType.REMOVED)
        assertEquals("Артефакт удален: Амулет", ProfileDiffer.formatMessage(change))
    }

    @Test
    fun formatMessage_changed_shortValue_includesValue() {
        val change = ProfileChange("Инструмент", "Карты", "Маятник", ChangeType.CHANGED)
        assertEquals("Инструмент изменен: Маятник", ProfileDiffer.formatMessage(change))
    }

    @Test
    fun formatMessage_changed_nullNewValue_usesPlaceholder() {
        val change = ProfileChange("Инструмент", "Карты", null, ChangeType.CHANGED)
        assertEquals("Инструмент изменен: не указан", ProfileDiffer.formatMessage(change))
    }

    @Test
    fun formatMessage_changed_longValue_omitsValue() {
        val longValue = "а".repeat(31)
        val change = ProfileChange("Инструмент", "Карты", longValue, ChangeType.CHANGED)
        assertEquals("Инструмент изменен", ProfileDiffer.formatMessage(change))
    }
}
