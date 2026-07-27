package bas.app.shift.helpers

import android.app.TimePickerDialog
import android.content.Context
import android.widget.TextView
import bas.app.shift.R
import java.util.Calendar

object TimePickerHelper {
    
    fun showTimePicker(
        context: Context,
        currentTime: String? = null,
        onTimeSelected: (hours: Int, minutes: Int) -> Unit
    ) {
        val (defaultHours, defaultMinutes) = if (currentTime != null && currentTime.isNotBlank()) {
            val minutes = parseTimeToMinutes(currentTime) ?: 0
            minutes / 60 to minutes % 60
        } else {
            0 to 0 // По умолчанию 00:00
        }
        
        val timePickerDialog = TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                onTimeSelected(hourOfDay, minute)
            },
            defaultHours,
            defaultMinutes,
            true // 24-часовой формат
        )
        
        timePickerDialog.setTitle(context.getString(R.string.time_picker_title))
        timePickerDialog.show()
    }
    
    fun formatTime(hours: Int, minutes: Int): String {
        return when {
            hours == 0 && minutes == 0 -> ""
            hours == 0 -> "${minutes}м"
            minutes == 0 -> "${hours}ч"
            else -> "${hours}ч ${minutes}м"
        }
    }
    
    fun parseTimeToMinutes(timeString: String): Int? {
        if (timeString.isBlank()) return null
        
        LogHelper.d("TimePickerHelper: Parsing timeString: '$timeString'")
        
        // Пробуем разные форматы
        val patterns = listOf(
            """(\d+)ч\s*(\d+)м""".toRegex(), // "2ч 30м"
            """(\d+)ч""".toRegex(),          // "2ч"
            """(\d+)м""".toRegex()           // "30м"
        )
        
        for (pattern in patterns) {
            val matchResult = pattern.find(timeString)
            if (matchResult != null) {
                val first = matchResult.groupValues[1].toIntOrNull() ?: 0
                val second = if (matchResult.groupValues.size > 2) {
                    matchResult.groupValues[2].toIntOrNull() ?: 0
                } else {
                    0
                }
                
                val totalMinutes = if (timeString.contains("ч")) {
                    first * 60 + second
                } else {
                    first
                }
                
                LogHelper.d("TimePickerHelper: Parsed $timeString -> first: $first, second: $second, total: $totalMinutes")
                return totalMinutes
            }
        }
        
        LogHelper.d("TimePickerHelper: Could not parse timeString: '$timeString'")
        return null
    }
}
