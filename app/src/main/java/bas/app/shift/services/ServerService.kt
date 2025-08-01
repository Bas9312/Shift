package bas.app.shift.services

import android.location.Location
import android.widget.Toast
import bas.app.shift.ShiftApplication
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.Point
import bas.app.shift.models.PointRequest
import bas.app.shift.models.UserLocation
import bas.app.shift.api.RetrofitClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import retrofit2.Response

object ServerService {
    private const val TAG = "ServerService"
    private val api = RetrofitClient.shiftApi
    private val scope = CoroutineScope(Dispatchers.IO)

    fun sendLocation(location: Location) {
        LogHelper.d("Отправка геолокации на сервер: ${location.latitude}, ${location.longitude}")
        scope.launch {
            try {
                val userId = UserPrefsHelper.getUserId(ShiftApplication.instance)
                val userName = UserPrefsHelper.getUserName(ShiftApplication.instance)
                
                val userLocation = UserLocation(
                    id = userId,
                    name = userName,
                    lat = location.latitude,
                    lng = location.longitude,
                    show = true
                )
                api.updateUserLocation(userLocation)
            } catch (e: Exception) {
                LogHelper.e("Ошибка при отправке геолокации: ${e.message}")
            }
        }
    }

    fun notifyHiddenEffectEnter(pointId: String, position: LatLng) {
        LogHelper.d("Вход в зону скрытого эффекта: $pointId, позиция: ${position.latitude}, ${position.longitude}")
        Toast.makeText(ShiftApplication.instance, "Вход в зону скрытого эффекта: $pointId, позиция: ${position.latitude}, ${position.longitude}", Toast.LENGTH_LONG).show()
        // TODO: Реализовать отправку уведомления о входе в зону скрытого эффекта
    }

    fun notifyHiddenEffectExit(pointId: String, position: LatLng) {
        LogHelper.d("Выход из зоны скрытого эффекта: $pointId, позиция: ${position.latitude}, ${position.longitude}")
        Toast.makeText(ShiftApplication.instance, "Выход из зоны скрытого эффекта: $pointId, позиция: ${position.latitude}, ${position.longitude}", Toast.LENGTH_LONG).show()
        // TODO: Реализовать отправку уведомления о выходе из зоны скрытого эффекта
    }

    fun getPoints(): List<Point> {
        LogHelper.d("Получение точек с сервера")
        return runBlocking {
            try {
                val response = api.getPoints()
                if (response.isSuccessful) {
                    response.body()?.points ?: emptyList()
                } else {
                    LogHelper.e("Ошибка при получении точек: ${response.code()}")
                    emptyList()
                }
            } catch (e: Exception) {
                LogHelper.e("Ошибка при получении точек: ${e.message}")
                emptyList()
            }
        }
    }

    suspend fun createPoint(pointRequest: PointRequest): Response<Point> {
        LogHelper.d("Создание новой точки: $pointRequest")
        return try {
            api.createPoint(pointRequest)
        } catch (e: Exception) {
            LogHelper.e("Ошибка при создании точки: ${e.message}")
            throw e
        }
    }

    suspend fun deletePoint(pointId: String): Response<Unit> {
        LogHelper.d("Удаление точки с ID: $pointId")
        return try {
            api.deletePoint(pointId)
        } catch (e: Exception) {
            LogHelper.e("Ошибка при удалении точки: ${e.message}")
            throw e
        }
    }
}