package bas.app.shift.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import bas.app.shift.models.User
import com.google.gson.Gson

object UserPrefsHelper {
    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_ID = "current_user_id"
    private const val KEY_USER_DATA = "current_user_data"
    private const val KEY_USER_NAME = "current_user_name"

    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_ID, "") ?: ""
    }

    fun setUserId(context: Context, userId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit() { putString(KEY_USER_ID, userId) }
    }

    fun saveUserData(context: Context, user: User) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val userJson = gson.toJson(user)
        
        prefs.edit {
            putString(KEY_USER_DATA, userJson)
            putString(KEY_USER_NAME, user.characterName ?: user.playerName ?: "")
        }
    }

    fun getUserData(context: Context): User? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userJson = prefs.getString(KEY_USER_DATA, null)
        
        return if (userJson != null) {
            try {
                val gson = Gson()
                gson.fromJson(userJson, User::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    fun getUserName(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_USER_NAME, "Unknown User") ?: "Unknown User"
    }

    fun hasUserData(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_USER_DATA)
    }

    fun clearUserData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            remove(KEY_USER_ID)
            remove(KEY_USER_DATA)
            remove(KEY_USER_NAME)
        }
    }
} 