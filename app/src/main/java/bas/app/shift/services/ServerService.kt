package bas.app.shift.services

import android.location.Location
import bas.app.shift.ShiftApplication
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.Point
import bas.app.shift.models.PointRequest
import bas.app.shift.models.UserLocation
import bas.app.shift.api.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
                val showOnMap = UserPrefsHelper.getShowOnMap(ShiftApplication.instance)
                
                val userLocation = UserLocation(
                    id = userId,
                    name = userName,
                    lat = location.latitude,
                    lng = location.longitude,
                    show = showOnMap
                )
                //LogHelper.d("Отправка геолокации с show = $showOnMap")
                val response = api.updateUserLocation(userLocation)
                if (!response.isSuccessful) {
                    LogHelper.e("Ошибка при отправке геолокации: ${response.code()}")
                }
            } catch (e: Exception) {
                LogHelper.e("Ошибка при отправке геолокации: ${e.message}")
            }
        }
    }

    /**
     * Возвращает список точек, или null при СЕТЕВОЙ ОШИБКЕ (в отличие от пустого списка —
     * «точек реально нет»). Вызывающий код по null должен сохранить прежнее состояние,
     * а не «выкидывать» игрока из всех зон при разовом обрыве сети.
     */
    suspend fun getPoints(): List<Point>? {
        return try {
            val userId = UserPrefsHelper.getUserId(ShiftApplication.instance)
            val response = api.getPoints(userId)
            if (response.isSuccessful) {
                response.body()?.points ?: emptyList()
            } else {
                LogHelper.e("Ошибка при получении точек: ${response.code()}")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogHelper.e("Ошибка при получении точек: ${e.message}")
            null
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

    /** Меняет только переданные поля: null означает «не трогать» (Gson их не сериализует). */
    suspend fun updatePoint(
        pointId: String,
        hidden: Boolean? = null,
        trackable: Boolean? = null,
        auraText: String? = null,
    ): Response<Point> {
        LogHelper.d("Обновление точки $pointId: hidden=$hidden, trackable=$trackable, aura=${auraText ?: "не трогаем"}")
        return try {
            api.updatePoint(pointId, bas.app.shift.models.UpdatePointRequest(hidden, trackable, auraText))
        } catch (e: Exception) {
            LogHelper.e("Ошибка при обновлении точки: ${e.message}")
            throw e
        }
    }

    /** Занять фамильяра под себя. 409 — с ним уже общается другой игрок. */
    suspend fun bindFamiliar(pointId: String, playerId: String): Response<Point> {
        LogHelper.d("Привязка фамильяра $pointId к игроку $playerId")
        return try {
            api.bindFamiliar(pointId, bas.app.shift.models.BindFamiliarRequest(playerId))
        } catch (e: Exception) {
            LogHelper.e("Ошибка при привязке фамильяра: ${e.message}")
            throw e
        }
    }

    /**
     * Продлевает привязку к фамильяру. Ошибки намеренно только логируются: не продлившийся
     * захват освободит фамильяра раньше срока, но помешать игроку писать — хуже.
     */
    fun touchFamiliar(pointId: String) {
        scope.launch {
            try {
                val response = api.touchFamiliar(pointId)
                if (!response.isSuccessful) {
                    LogHelper.w("Не удалось продлить привязку к фамильяру $pointId: ${response.code()}")
                }
            } catch (e: Exception) {
                LogHelper.w("Не удалось продлить привязку к фамильяру $pointId: ${e.message}")
            }
        }
    }
}