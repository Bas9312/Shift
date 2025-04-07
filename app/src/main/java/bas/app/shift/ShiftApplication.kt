package bas.app.shift

import android.app.Application
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModelProvider.NewInstanceFactory.Companion.instance
import bas.app.shift.MainActivity.Companion.KEY_IN_GAME
import bas.app.shift.MainActivity.Companion.PREFS_NAME
import bas.app.shift.helpers.AndroidStandardLogger
import bas.app.shift.helpers.LogHelper
import bas.app.shift.services.LocationService

class ShiftApplication : Application() {

    fun isInGame(): Boolean = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .getBoolean(KEY_IN_GAME, true)

    fun setIsInGame(isInGame: Boolean) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IN_GAME, isInGame)
            .apply()
    }

    companion object {
        @JvmStatic
        lateinit var instance: ShiftApplication
            private set
    }

    init {
        instance = this
    }

    fun startLocationService() {
        startService(Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_START
        })
    }

    fun stopLocationService() {
        startService(Intent(this, LocationService::class.java).apply {
            action = LocationService.ACTION_STOP
        })
    }

    override fun onCreate() {
        super.onCreate()
        LogHelper.addLogger(AndroidStandardLogger())
        LogHelper.d("onCreate ")
        if (isInGame()) {
            startLocationService()
        }
    }

}