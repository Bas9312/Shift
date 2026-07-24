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
    
    fun adjustNoise(delta: Double) {
        val currentUserId = userId ?: return
        // selfDelta — та часть шума, что в итоге придётся на самого пользователя.
        // Каждый активный эффект «отщипывает» от неё половину в пользу другого узла.
        // Важно: суммарный шум сохраняется, и себе он начисляется ровно один раз (в конце),
        // без прежнего двойного начисления при ненайденном Cross-Link партнёре.
        var selfDelta = delta

        // Proxy: половина уходит на Proxy-узел
        val hasProxyEffect = noiseEffectManager.hasProxyEffect()
        if (hasProxyEffect && selfDelta > 0) {
            val proxyDelta = selfDelta / 2.0
            selfDelta -= proxyDelta
            LogHelper.d("NoiseManager: Proxy effect active, splitting $delta: $proxyDelta -> proxy, остаток $selfDelta")
            adjustNoiseForUser("${currentUserId}_Proxy", proxyDelta)
        }

        // Cross-Link: половина оставшегося уходит партнёру
        val hasCrossLinkEffect = noiseEffectManager.hasCrossLinkEffect()
        if (hasCrossLinkEffect && selfDelta > 0) {
            val partnerName = noiseEffectManager.getCrossLinkPartnerName()
            if (partnerName != null) {
                val partnerDelta = selfDelta / 2.0
                selfDelta -= partnerDelta
                LogHelper.d("NoiseManager: Cross-Link active, $partnerDelta -> партнёр '$partnerName', остаток $selfDelta")
                findUserByName(partnerName) { partnerId ->
                    if (partnerId != null) {
                        adjustNoiseForUser(partnerId, partnerDelta)
                    } else {
                        // Партнёр не найден — возвращаем его долю себе, чтобы не потерять шум
                        LogHelper.e("NoiseManager: partner ID для '$partnerName' не найден, доля возвращается пользователю")
                        adjustNoiseForUser(currentUserId, partnerDelta)
                    }
                }
            } else {
                LogHelper.e("NoiseManager: Cross-Link активен, но имя партнёра не найдено")
            }
        }

        adjustNoiseForUser(currentUserId, selfDelta)
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
        onGlobalNoiseUpdateListener = null
        onCommandSuccessListener = null
    }
}
