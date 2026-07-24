package bas.app.shift.helpers

/**
 * Единая склейка «Имя персонажа / Имя игрока». Логика повторялась ~4 раза по проекту
 * (список чатов, профиль МГ, заголовки) — теперь одна реализация.
 */
object DisplayNames {
    /**
     * @param character имя персонажа, @param player имя игрока, @param fallback запасной вариант (id).
     * Возвращает "Персонаж / Игрок", либо то из них, что есть, либо fallback.
     */
    fun combine(character: String?, player: String?, fallback: String): String = when {
        !character.isNullOrEmpty() && !player.isNullOrEmpty() -> "$character / $player"
        !character.isNullOrEmpty() -> character
        !player.isNullOrEmpty() -> player
        else -> fallback
    }

    /**
     * То же самое, но в порядке "Игрок / Персонаж" — используется в списках выбора
     * пользователя на МГ-экранах (спиннеры/автодополнение).
     */
    fun combinePlayerFirst(player: String?, character: String?, fallback: String): String = when {
        !player.isNullOrEmpty() && !character.isNullOrEmpty() -> "$player / $character"
        !player.isNullOrEmpty() -> player
        !character.isNullOrEmpty() -> character
        else -> fallback
    }
}
