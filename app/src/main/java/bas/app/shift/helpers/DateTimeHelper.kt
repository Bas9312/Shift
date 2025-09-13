package bas.app.shift.helpers

import java.text.SimpleDateFormat
import java.util.*

object DateTimeHelper {
    
    private const val SERVER_TIMEZONE_OFFSET = -2 // Сервер на 2 часа раньше нашего времени
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
            serverFormat.timeZone = TimeZone.getTimeZone("UTC-2")
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
    
    fun isExpired(expireAt: String?): Boolean {
        if (expireAt == null || expireAt.isBlank()) return false
        
        return try {
            val serverFormat = SimpleDateFormat(DATE_FORMAT, Locale.getDefault())
            serverFormat.timeZone = TimeZone.getTimeZone("UTC-2")
            val expireDate = serverFormat.parse(expireAt)
            
            if (expireDate != null) {
                val now = Date()
                now.after(expireDate)
            } else {
                false
            }
        } catch (e: Exception) {
            LogHelper.e("Error checking expiration: $expireAt, $e")
            false
        }
    }
}
