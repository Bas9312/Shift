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
    private var onCommandSuccessListener: (() -> Unit)? = null
    private val noiseEffectManager = NoiseEffectManager(context)
    
    private val handler = Handler(Looper.getMainLooper())
    private var noiseUpdateRunnable: Runnable? = null
    
    fun setUserId(userId: String) {
        this.userId = userId
    }
    
    fun setOnNoiseUpdateListener(listener: (Double) -> Unit) {
        this.onNoiseUpdateListener = listener
    }
    
    fun setOnCommandSuccessListener(listener: () -> Unit) {
        this.onCommandSuccessListener = listener
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
                        LogHelper.d("NoiseManager: Current noise updated from $previousNoise to $currentNoise (periodic fetch)")
                    } else {
                        LogHelper.e("NoiseManager: Error fetching noise: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<NoiseState>, t: Throwable) {
                    LogHelper.e("NoiseManager: Error fetching noise: ${t.message}")
                }
            })
    }
    
    fun adjustNoise(delta: Double) {
        val currentUserId = userId ?: return
        var currentDelta = delta
        
        // Проверяем, есть ли Proxy эффект
        val hasProxyEffect = noiseEffectManager.hasProxyEffect()
        
        if (hasProxyEffect && delta > 0) {
            // Если есть Proxy эффект и шум положительный - делим пополам
            currentDelta = delta / 2.0
            val proxyUserId = "${currentUserId}_Proxy"
            
            LogHelper.d("NoiseManager: Proxy effect active, splitting noise: $delta -> $currentDelta for user and proxy")
            
            // Отправляем половину шума Proxy узлу
            adjustNoiseForUser(proxyUserId, currentDelta)
        }

        val hasCrossLinkEffect = noiseEffectManager.hasCrossLinkEffect()
        if (hasCrossLinkEffect && currentDelta > 0) {
            // Проверяем, есть ли Cross-Link эффект

                // Если есть Cross-Link эффект и шум положительный - делим пополам
            currentDelta /= 2.0
                val partnerName = noiseEffectManager.getCrossLinkPartnerName()

                if (partnerName != null) {
                    LogHelper.d("NoiseManager: Cross-Link effect active, splitting noise: ${currentDelta * 2} -> $currentDelta for user and partner")

                    // Отправляем половину шума пользователю

                    // Находим ID партнера и отправляем половину шума ему
                    findUserByName(partnerName) { partnerId ->
                        if (partnerId != null) {
                            adjustNoiseForUser(partnerId, currentDelta)
                        } else {
                            LogHelper.e("NoiseManager: Could not find partner ID for name: $partnerName")
                        }
                    }
                } else {
                    LogHelper.e("NoiseManager: Cross-Link effect active but partner name not found")
                    adjustNoiseForUser(currentUserId, delta)
                }
            } else {
                // Обычная отправка шума
                adjustNoiseForUser(currentUserId, delta)
            }
        adjustNoiseForUser(currentUserId, currentDelta)
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
                            onCommandSuccessListener?.invoke()
                        }
                        
                        LogHelper.d("NoiseManager: Noise adjusted for $targetUserId: ${adjustResponse.local.before} -> ${adjustResponse.local.after}")
                    } else {
                        LogHelper.e("NoiseManager: Error adjusting noise for $targetUserId: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.NoiseAdjustResponse>, t: Throwable) {
                    LogHelper.e("NoiseManager: Error adjusting noise for $targetUserId: ${t.message}")
                }
            })
    }
    
    fun getCurrentNoise(): Double = currentNoise
    
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
     * Находит пользователя по имени персонажа
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
        onCommandSuccessListener = null
    }
}
