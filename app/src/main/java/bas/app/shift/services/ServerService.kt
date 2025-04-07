package bas.app.shift.services

import android.location.Location
import android.widget.Toast
import bas.app.shift.ShiftApplication
import bas.app.shift.helpers.LogHelper
import bas.app.shift.models.PointOfInterest
import com.google.android.gms.maps.model.LatLng

object ServerService {
    private const val TAG = "ServerService"

    fun sendLocation(location: Location) {
        LogHelper.d("Отправка геолокации на сервер: ${location.latitude}, ${location.longitude}")
        // TODO: Реализовать отправку геолокации на сервер
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

    fun getPointsOfInterest(): List<PointOfInterest> {
        LogHelper.d("Получение точек интереса с сервера")
        // TODO: Реализовать получение точек интереса с сервера
        return emptyList() // В реальности здесь будет запрос к серверу
    }
} 