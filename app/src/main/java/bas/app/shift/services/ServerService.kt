package bas.app.shift.services

import android.location.Location
import android.widget.Toast
import bas.app.shift.ShiftApplication
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.Point
import bas.app.shift.models.UserLocation
import com.example.shift.data.api.RetrofitClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object ServerService {
    private const val TAG = "ServerService"
    private val api = RetrofitClient.shiftApi
    private val scope = CoroutineScope(Dispatchers.IO)

    fun sendLocation(location: Location) {
        LogHelper.d("Отправка геолокации на сервер: ${location.latitude}, ${location.longitude}")
        scope.launch {
            try {
                val userLocation = UserLocation(
                    id = "9312",
                    name = "TestUser",
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
}