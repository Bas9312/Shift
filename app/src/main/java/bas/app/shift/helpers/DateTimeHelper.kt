package bas.app.shift.helpers

import java.text.SimpleDateFormat
import java.util.*

object DateTimeHelper {
    
    private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    
    fun formatExpireAt(expireAt: String?): String? {
        if (expireAt == null || expireAt.isBlank()) {
            LogHelper.d("DateTimeHelper: ExpireAt is null or blank")
            return null
        }
        
        LogHelper.d("DateTimeHelper: Formatting expireAt: '$expireAt'")
        
        return try {
            // Парсим дату с сервера (в UTC-2)
            val serverFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val date = serverFormat.parse(expireAt)
            
            LogHelper.d("DateTimeHelper: Parsed date: $date")
            
            if (date != null) {
                // Конвертируем в локальное время
                val localFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                val result = localFormat.format(date)
                LogHelper.d("DateTimeHelper: Formatted result: '$result'")
                result
            } else {
                LogHelper.d("DateTimeHelper: Date is null, returning original")
                expireAt // Fallback к исходной строке
            }
        } catch (e: Exception) {
            LogHelper.e("DateTimeHelper: Error parsing expireAt: '$expireAt' - $e")
            // Возвращаем исходную строку как есть, если не можем распарсить
            expireAt
        }
    }

    /**
     * Серверный timestamp ("yyyy-MM-dd HH:mm:ss") -> "HH:mm" для списков чатов/сообщений.
     * При ошибке парсинга возвращает исходную строку как есть.
     */
    fun formatMessageTime(createdAt: String): String {
        return try {
            val inputFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val date = inputFormat.parse(createdAt)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            createdAt
        }
    }

}
