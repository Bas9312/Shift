package bas.app.shift.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

object UserPrefsHelper {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_ID = "current_user_id"

    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    fun setUserId(context: Context, userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() { putString(KEY_USER_ID, userId) }
    }
} 