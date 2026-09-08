package bas.app.shift.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.NoiseAdjustRequest
import bas.app.shift.models.NoiseState
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class NoiseManager(private val context: Context) {
    
    private var currentNoise = 0.0
    private var previousNoise = 0.0
    private var userId: String? = null
    private var onNoiseUpdateListener: ((Double) -> Unit)? = null
    private var onGlobalNoiseUpdateListener: ((Double) -> Unit)? = null
    private var onCommandSuccessListener: (() -> Unit)? = null
    private var onCommandFailureListener: ((String) -> Unit)? = null
    private val noiseEffectManager = NoiseEffectManager(context)
    
    private val handler = Handler(Looper.getMainLooper())
    private var noiseUpdateRunnable: Runnable? = null
    
    fun setUserId(userId: String) {
        this.userId = userId
    }
    
    fun setOnNoiseUpdateListener(listener: (Double) -> Unit) {
        this.onNoiseUpdateListener = listener
    }
    
    fun setOnGlobalNoiseUpdateListener(listener: (Double) -> Unit) {
        this.onGlobalNoiseUpdateListener = listener
    }
    
    fun setOnCommandSuccessListener(listener: () -> Unit) {
        this.onCommandSuccessListener = listener
    }

    /** Вызывается, когда изменение шума ГЛАВНОГО пользователя не удалось применить на сервере. */
    fun setOnCommandFailureListener(listener: (String) -> Unit) {
        this.onCommandFailureListener = listener
    }
    
    fun startPeriodicNoiseUpdate() {
        stopPeriodicNoiseUpdate()
        noiseUpdateRunnable = object : Runnable {
            override fun run() {
                fetchCurrentNoise()
                handler.postDelayed(this, 60000) // Каждую минуту
            }
        }
        handler.post(noiseUpdateRunnable!!)
    }
    
    fun stopPeriodicNoiseUpdate() {
        noiseUpdateRunnable?.let { handler.removeCallbacks(it) }
        noiseUpdateRunnable = null
    }
    
    fun fetchCurrentNoise() {
        val currentUserId = userId ?: return
        
        RetrofitClient.noiseApi.getUserNoise(currentUserId)
            .enqueue(object : Callback<NoiseState> {
                override fun onResponse(call: Call<NoiseState>, response: Response<NoiseState>) {
                    if (response.isSuccessful && response.body() != null) {
                        val noiseState = response.body()!!
                        previousNoise = currentNoise
                        currentNoise = noiseState.localNoise
                        
                        // Для fetchCurrentNoise не проверяем эффекты, так как это периодическое обновление
                        // Эффекты проверяются только при adjustNoise, где у нас есть точные before/after значения
                        
                        onNoiseUpdateListener?.invoke(currentNoise)
                        onGlobalNoiseUpdateListener?.invoke(noiseState.globalNoise)
                        LogHelper.d("NoiseManager: Current noise updated from $previousNoise to $currentNoise, global: ${noiseState.globalNoise} (periodic fetch)")
                    } else {
                        LogHelper.e("NoiseManager: Error fetching noise: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    LogHelper.e("NoiseManager: Error fetching noise: ${t.message}")
                }
            })
    }
    
    /**
     * Шлём серверу полный прирост одним запросом. Делит его сервер: он же начисляет шум
     * с сайта, где приложения нет вовсе, а два независимых деления разъехались бы.
     * Эффекты Proxy и Cross-Link сервер читает сам из тех же записей, которые ставит
     * приложение через effects_api.
     */
    fun adjustNoise(delta: Double) {
        val currentUserId = userId ?: return
        adjustNoiseForUser(currentUserId, delta)
    }
    
    private fun adjustNoiseForUser(targetUserId: String, delta: Double) {
        val request = NoiseAdjustRequest(delta = delta)
        
        RetrofitClient.noiseApi.adjustUserNoise(targetUserId, request)
            .enqueue(object : Callback<bas.app.shift.models.NoiseAdjustResponse> {
                override fun onResponse(
                    call: Call<bas.app.shift.models.NoiseAdjustResponse>, 
                    response: Response<bas.app.shift.models.NoiseAdjustResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val adjustResponse = response.body()!!
                        val serverBeforeNoise = adjustResponse.local.before
                        val newNoise = adjustResponse.local.after
                        
                        // Обновляем currentNoise только если это основной пользователь
                        if (targetUserId == userId) {
                            currentNoise = newNoise
                            
                            // Проверяем эффекты при изменении шума только для основного пользователя
                            if (userId != null) {
                                LogHelper.d("NoiseManager: Calling NoiseEffectManager with serverBeforeNoise: $serverBeforeNoise, currentNoise: $currentNoise, userId: $userId")
                                noiseEffectManager.checkAndApplyNoiseEffects(serverBeforeNoise, currentNoise, userId!!)
                            } else {
                                LogHelper.e("NoiseManager: userId is null, cannot check noise effects")
                            }
                            
                            onNoiseUpdateListener?.invoke(currentNoise)
                            onGlobalNoiseUpdateListener?.invoke(adjustResponse.global.after)
                            onCommandSuccessListener?.invoke()
                        }
                        
                        LogHelper.d("NoiseManager: Noise adjusted for $targetUserId: ${adjustResponse.local.before} -> ${adjustResponse.local.after}")
                    } else {
                        LogHelper.e("NoiseManager: Error adjusting noise for $targetUserId: ${response.code()}")
                        if (targetUserId == userId) {
                            onCommandFailureListener?.invoke(NetworkErrors.http(response.code()))
                        }
                    }
                }

                override fun onFailure(call: Call<bas.app.shift.models.NoiseAdjustResponse>, t: Throwable) {
                    LogHelper.e("NoiseManager: Error adjusting noise for $targetUserId: ${t.message}")
                    if (targetUserId == userId) {
                        onCommandFailureListener?.invoke(NetworkErrors.network(t))
                    }
                }
            })
    }
    
    fun hasProxyEffect(): Boolean {
        return noiseEffectManager.hasProxyEffect()
    }
    
    fun applyProxyEffect(userId: String) {
        noiseEffectManager.applyProxyEffect(userId)
    }
    
    fun hasCrossLinkEffect(): Boolean {
        return noiseEffectManager.hasCrossLinkEffect()
    }
    
    fun applyCrossLinkEffect(userId1: String, userId2: String, partnerName1: String, partnerName2: String) {
        noiseEffectManager.applyCrossLinkEffect(userId1, userId2, partnerName1, partnerName2)
    }
    
    /**
     * Находит пользователя по имени персонажа.
     * Для деления шума больше не нужен — партнёра по Cross-Link ищет сервер; оставлен как
     * общий помощник, если понадобится другому экрану.
     */
    fun findUserByName(characterName: String, callback: (String?) -> Unit) {
        RetrofitClient.userProfileApi.getAllUserShortProfiles()
            .enqueue(object : Callback<List<bas.app.shift.models.ShortUser>> {
                override fun onResponse(call: Call<List<bas.app.shift.models.ShortUser>>, response: Response<List<bas.app.shift.models.ShortUser>>) {
                    if (response.isSuccessful && response.body() != null) {
                        val users = response.body()!!
                        val foundUser = users.find { it.characterName == characterName }
                        callback(foundUser?.userId)
                    } else {
                        callback(null)
                    }
                }

                override fun onFailure(call: Call<List<bas.app.shift.models.ShortUser>>, t: Throwable) {
                    callback(null)
                }
            })
    }
    
    fun cleanup() {
        stopPeriodicNoiseUpdate()
        onNoiseUpdateListener = null
        onGlobalNoiseUpdateListener = null
        onCommandSuccessListener = null
        onCommandFailureListener = null
    }
}
