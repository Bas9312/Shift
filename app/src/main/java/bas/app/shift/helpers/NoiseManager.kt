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
    
    fun adjustNoise(delta: Int) {
        val currentUserId = userId ?: return
        
        val request = NoiseAdjustRequest(delta = delta)
        
        RetrofitClient.noiseApi.adjustUserNoise(currentUserId, request)
            .enqueue(object : Callback<bas.app.shift.models.NoiseAdjustResponse> {
                override fun onResponse(
                    call: Call<bas.app.shift.models.NoiseAdjustResponse>, 
                    response: Response<bas.app.shift.models.NoiseAdjustResponse>
                ) {
                    if (response.isSuccessful && response.body() != null) {
                        val adjustResponse = response.body()!!
                        val serverBeforeNoise = adjustResponse.local.before
                        currentNoise = adjustResponse.local.after
                        
                        // Проверяем эффекты при изменении шума
                        if (userId != null) {
                            LogHelper.d("NoiseManager: Calling NoiseEffectManager with serverBeforeNoise: $serverBeforeNoise, currentNoise: $currentNoise, userId: $userId")
                            noiseEffectManager.checkAndApplyNoiseEffects(serverBeforeNoise, currentNoise, userId!!)
                        } else {
                            LogHelper.e("NoiseManager: userId is null, cannot check noise effects")
                        }
                        
                        onNoiseUpdateListener?.invoke(currentNoise)
                        onCommandSuccessListener?.invoke()
                        LogHelper.d("NoiseManager: Noise adjusted: ${adjustResponse.local.before} -> ${adjustResponse.local.after}")
                    } else {
                        LogHelper.e("NoiseManager: Error adjusting noise: ${response.code()}")
                    }
                }
                
                override fun onFailure(call: Call<bas.app.shift.models.NoiseAdjustResponse>, t: Throwable) {
                    LogHelper.e("NoiseManager: Error adjusting noise: ${t.message}")
                }
            })
    }
    
    fun getCurrentNoise(): Double = currentNoise
    
    fun cleanup() {
        stopPeriodicNoiseUpdate()
        onNoiseUpdateListener = null
        onCommandSuccessListener = null
    }
}
