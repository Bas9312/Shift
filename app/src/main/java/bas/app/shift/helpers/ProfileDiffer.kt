package bas.app.shift.helpers

import bas.app.shift.models.User

data class ProfileChange(
    val fieldName: String,
    val oldValue: String?,
    val newValue: String?,
    val changeType: ChangeType
)

enum class ChangeType {
    ADDED, REMOVED, CHANGED
}

/**
 * Сравнение двух версий профиля пользователя для уведомлений об изменениях
 * (новая дисциплина, эффект, артефакт и т.д.). Чистая логика без зависимости от Android —
 * вынесена из LocationService, чтобы не путать сетевой поллинг с построением диффа.
 */
object ProfileDiffer {

    fun diff(oldProfile: User, newProfile: User): List<ProfileChange> {
        val changes = mutableListOf<ProfileChange>()

        if (oldProfile.disciplines != newProfile.disciplines) {
            val oldDisciplines = oldProfile.disciplines.map { it.name }.sorted()
            val newDisciplines = newProfile.disciplines.map { it.name }.sorted()

            if (oldDisciplines != newDisciplines) {
                val added = newDisciplines - oldDisciplines
                val removed = oldDisciplines - newDisciplines

                added.forEach { disciplineName ->
                    changes.add(ProfileChange(
                        fieldName = "Дисциплина",
                        oldValue = null,
                        newValue = disciplineName,
                        changeType = ChangeType.ADDED
                    ))
                }

                removed.forEach { disciplineName ->
                    changes.add(ProfileChange(
                        fieldName = "Дисциплина",
                        oldValue = disciplineName,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }

        if (oldProfile.modules != newProfile.modules) {
            val oldModules = oldProfile.modules.map { it.name }.sorted()
            val newModules = newProfile.modules.map { it.name }.sorted()

            if (oldModules != newModules) {
                val added = newModules - oldModules
                val removed = oldModules - newModules

                added.forEach { moduleName ->
                    changes.add(ProfileChange(
                        fieldName = "Модуль",
                        oldValue = null,
                        newValue = moduleName,
                        changeType = ChangeType.ADDED
                    ))
                }

                removed.forEach { moduleName ->
                    changes.add(ProfileChange(
                        fieldName = "Модуль",
                        oldValue = moduleName,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }

        if (oldProfile.abilities != newProfile.abilities) {
            val oldAbilities = oldProfile.abilities.map { "${it.type}: ${it.description}" }.sorted()
            val newAbilities = newProfile.abilities.map { "${it.type}: ${it.description}" }.sorted()

            if (oldAbilities != newAbilities) {
                val added = newAbilities - oldAbilities
                val removed = oldAbilities - newAbilities

                added.forEach { ability ->
                    changes.add(ProfileChange(
                        fieldName = "Способность",
                        oldValue = null,
                        newValue = ability,
                        changeType = ChangeType.ADDED
                    ))
                }

                removed.forEach { ability ->
                    changes.add(ProfileChange(
                        fieldName = "Способность",
                        oldValue = ability,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }

        if (oldProfile.artifacts != newProfile.artifacts) {
            val oldArtifacts = oldProfile.artifacts.map { it.name }.sorted()
            val newArtifacts = newProfile.artifacts.map { it.name }.sorted()

            if (oldArtifacts != newArtifacts) {
                val added = newArtifacts - oldArtifacts
                val removed = oldArtifacts - newArtifacts

                added.forEach { artifact ->
                    changes.add(ProfileChange(
                        fieldName = "Артефакт",
                        oldValue = null,
                        newValue = artifact,
                        changeType = ChangeType.ADDED
                    ))
                }

                removed.forEach { artifact ->
                    changes.add(ProfileChange(
                        fieldName = "Артефакт",
                        oldValue = artifact,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }

        if (oldProfile.instrument != newProfile.instrument) {
            changes.add(ProfileChange(
                fieldName = "Инструмент",
                oldValue = oldProfile.instrument,
                newValue = newProfile.instrument,
                changeType = ChangeType.CHANGED
            ))
        }

        if (oldProfile.familiar != newProfile.familiar) {
            changes.add(ProfileChange(
                fieldName = "Фамильяр",
                oldValue = oldProfile.familiar,
                newValue = newProfile.familiar,
                changeType = ChangeType.CHANGED
            ))
        }

        if (oldProfile.misc != newProfile.misc) {
            val oldMisc = oldProfile.misc.sorted()
            val newMisc = newProfile.misc.sorted()

            if (oldMisc != newMisc) {
                val added = newMisc - oldMisc
                val removed = oldMisc - newMisc

                added.forEach { miscItem ->
                    changes.add(ProfileChange(
                        fieldName = "Доп. параметр",
                        oldValue = null,
                        newValue = miscItem,
                        changeType = ChangeType.ADDED
                    ))
                }

                removed.forEach { miscItem ->
                    changes.add(ProfileChange(
                        fieldName = "Доп. параметр",
                        oldValue = miscItem,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }

        if (oldProfile.playerName != newProfile.playerName) {
            changes.add(ProfileChange(
                fieldName = "Имя игрока",
                oldValue = oldProfile.playerName,
                newValue = newProfile.playerName,
                changeType = ChangeType.CHANGED
            ))
        }

        if (oldProfile.characterName != newProfile.characterName) {
            changes.add(ProfileChange(
                fieldName = "Имя персонажа",
                oldValue = oldProfile.characterName,
                newValue = newProfile.characterName,
                changeType = ChangeType.CHANGED
            ))
        }

        if (oldProfile.effects != newProfile.effects) {
            val oldEffects = oldProfile.effects?.map { it.textToShowPlayers }?.sorted() ?: emptyList()
            val newEffects = newProfile.effects?.map { it.textToShowPlayers }?.sorted() ?: emptyList()

            if (oldEffects != newEffects) {
                val added = newEffects - oldEffects
                val removed = oldEffects - newEffects

                added.forEach { effectText ->
                    changes.add(ProfileChange(
                        fieldName = "Эффект",
                        oldValue = null,
                        newValue = effectText,
                        changeType = ChangeType.ADDED
                    ))
                }

                removed.forEach { effectText ->
                    changes.add(ProfileChange(
                        fieldName = "Эффект",
                        oldValue = effectText,
                        newValue = null,
                        changeType = ChangeType.REMOVED
                    ))
                }
            }
        }

        return changes
    }

    fun formatMessage(change: ProfileChange): String {
        return when (change.changeType) {
            ChangeType.ADDED -> {
                if (change.fieldName == "Эффект") {
                    "Получен новый эффект: ${change.newValue}"
                } else {
                    "${change.fieldName} добавлен: ${change.newValue}"
                }
            }
            ChangeType.REMOVED -> {
                if (change.fieldName == "Эффект") {
                    "Эффект исчез: ${change.oldValue}"
                } else {
                    "${change.fieldName} удален: ${change.oldValue}"
                }
            }
            ChangeType.CHANGED -> {
                val newValue = change.newValue ?: "не указан"
                if (newValue.length <= 30) {
                    "${change.fieldName} изменен: $newValue"
                } else {
                    "${change.fieldName} изменен"
                }
            }
        }
    }
}
