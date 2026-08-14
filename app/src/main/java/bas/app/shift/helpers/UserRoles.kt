package bas.app.shift.helpers

/**
 * Проверка роли МГ по префиксу id. Логика (`startsWith("MG_")`) повторялась в 4 файлах
 * (чат, адаптер сообщений, поллинг новых сообщений, главный экран) — теперь одна реализация.
 */
object UserRoles {
    private const val MG_PREFIX = "MG_"

    fun isMg(userId: String?): Boolean = userId != null && userId.startsWith(MG_PREFIX)
}
