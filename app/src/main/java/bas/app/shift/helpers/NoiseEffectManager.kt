package bas.app.shift.helpers

import android.content.Context
import bas.app.shift.api.RetrofitClient
import bas.app.shift.models.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class NoiseEffectManager(private val context: Context) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    companion object {
        // Константы для текстов эффектов
        private const val LEVEL_3_EFFECT_TEXT = "Лёгкая потеря концентрации, в глазах немного рябит, техника с которой работаешь немного глючит"
        private const val LEVEL_4_EFFECT_TEXT = "У тебя пошла носом кровь, ты потерял 1 хит (разово, в момент получения эффекта). Сильная головная боль, электронные устройства в метре от тебя ведут себя хаотично, иногда перезагружаются (озвучивай при необходимости). Пока эффект активен - включи на телефоне негромкий звук любых помех/шума и ходи с включённым звуком (можно менять если напрягает, но человеку рядом с тобой должно быть его слышно)."
        private const val LEVEL_5_EFFECT_TEXT = "У тебя перегрузка нервной системы, ты падаешь в тяжёлое ранение (разово, в момент получения эффекта) и плохо соображаешь. При получении эффекта - все электронные устройства в радиусе 3 метров от тебя - выходят из строя (кроме Капсулы, озвучь окружающим). Дальше, пока эффект активен - вся электроника в метре от тебя выключается, и вновь начинает работать только когда окажется от тебя дальше 1 метра (также кроме Капсулы). Ты не можешь пользоваться Шумомантией пока эффект не пройдёт или не будет снят"
        
        // Константа для Proxy эффекта
        const val PROXY_EFFECT_TEXT = "Узел Proxy установлен и работает"
        
        // Константа для Cross-Link эффекта (с регулярным выражением)
        const val CROSS_LINK_EFFECT_PATTERN = "Связь с .+ установлена, шум делится пополам"
    }
    
    /**
     * Проверяет и применяет эффекты при изменении уровня шума
     */
    fun checkAndApplyNoiseEffects(oldNoise: Double, newNoise: Double, userId: String) {
        val oldLevel = NoiseHelper.getNoiseLevel(oldNoise)
        val newLevel = NoiseHelper.getNoiseLevel(newNoise)
        
        LogHelper.d("NoiseEffectManager: Checking effects - oldNoise: $oldNoise (level $oldLevel) -> newNoise: $newNoise (level $newLevel)")
        
        // Проверяем переходы на новые уровни
        when {
            oldLevel < 3 && newLevel >= 3 -> {
                LogHelper.d("NoiseEffectManager: Triggering level 3 effect")
                checkAndApplyLevel3Effect(userId)
            }
            oldLevel < 4 && newLevel >= 4 -> {
                LogHelper.d("NoiseEffectManager: Triggering level 4 effect")
                checkAndApplyLevel4Effect(userId)
            }
            oldLevel < 5 && newLevel >= 5 -> {
                LogHelper.d("NoiseEffectManager: Triggering level 5 effect")
                checkAndApplyLevel5Effect(userId)
            }
            else -> {
                LogHelper.d("NoiseEffectManager: No effect triggered")
            }
        }
    }
    
    private fun checkAndApplyLevel3Effect(userId: String) {
        scope.launch {
            try {
                LogHelper.d("NoiseEffectManager: Checking level 3 effect for user $userId")
                
                // Проверяем, есть ли уже такой эффект
                val userProfile = UserPrefsHelper.getUserData(context)
                LogHelper.d("NoiseEffectManager: User profile loaded: ${userProfile != null}")
                LogHelper.d("NoiseEffectManager: User effects count: ${userProfile?.effects?.size ?: 0}")
                
                val hasLevel3Effect = userProfile?.effects?.any { 
                    it.textToShowPlayers == LEVEL_3_EFFECT_TEXT
                } ?: false
                
                LogHelper.d("NoiseEffectManager: Has level 3 effect: $hasLevel3Effect")
                
                if (!hasLevel3Effect) {
                    LogHelper.d("NoiseEffectManager: Applying level 3 effect")
                    applyLevel3Effect(userId)
                } else {
                    LogHelper.d("NoiseEffectManager: Level 3 effect already exists, skipping")
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error checking level 3 effect: ${e.message}")
            }
        }
    }
    
    private fun checkAndApplyLevel4Effect(userId: String) {
        scope.launch {
            try {
                // Проверяем, есть ли уже такой эффект
                val userProfile = UserPrefsHelper.getUserData(context)
                val hasLevel4Effect = userProfile?.effects?.any { 
                    it.textToShowPlayers == LEVEL_4_EFFECT_TEXT
                } ?: false
                
                if (!hasLevel4Effect) {
                    applyLevel4Effect(userId)
                } else {
                    LogHelper.d("NoiseEffectManager: Level 4 effect already exists, skipping")
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error checking level 4 effect: ${e.message}")
            }
        }
    }
    
    private fun checkAndApplyLevel5Effect(userId: String) {
        scope.launch {
            try {
                // Проверяем, есть ли уже такой эффект
                val userProfile = UserPrefsHelper.getUserData(context)
                val hasLevel5Effect = userProfile?.effects?.any { 
                    it.textToShowPlayers == LEVEL_5_EFFECT_TEXT
                } ?: false
                
                if (!hasLevel5Effect) {
                    applyLevel5Effect(userId)
                } else {
                    LogHelper.d("NoiseEffectManager: Level 5 effect already exists, skipping")
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error checking level 5 effect: ${e.message}")
            }
        }
    }
    
    private fun applyLevel3Effect(userId: String) {
        scope.launch {
            try {
                LogHelper.d("NoiseEffectManager: Creating level 3 effect request for user $userId")
                
                // Создаем эффект "Лёгкая потеря концентрации"
                val effectRequest = EffectRequest(
                    textToShowPlayers = LEVEL_3_EFFECT_TEXT,
                    timeToLiveInMinutes = 30,
                    mark = null
                )
                
                LogHelper.d("NoiseEffectManager: Sending effect request to server")
                val response = RetrofitClient.effectApi.createEffect(userId, effectRequest)
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        LogHelper.d("NoiseEffectManager: Level 3 effect applied successfully")
                    } else {
                        LogHelper.e("NoiseEffectManager: Failed to apply level 3 effect: ${response.code()}")
                        LogHelper.e("NoiseEffectManager: Response body: ${response.errorBody()?.string()}")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error applying level 3 effect: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private fun applyLevel4Effect(userId: String) {
        scope.launch {
            try {
                // Создаем эффект "Кровь из носа и головная боль"
                val effectRequest = EffectRequest(
                    textToShowPlayers = LEVEL_4_EFFECT_TEXT,
                    timeToLiveInMinutes = 60,
                    mark = null
                )
                
                val effectResponse = RetrofitClient.effectApi.createEffect(userId, effectRequest)
                
                if (effectResponse.isSuccessful) {
                    // Добавляем проблему в ауру - Разрыв
                    val problemRequest = AuraProblemRequest(
                        slot = null,
                        problemType = AuraProblemType.TEAR,
                        name = "Разрыв",
                        description = "Получено от высокого уровня шума, аура повредилась"
                    )
                    
                    val problemResponse = RetrofitClient.auraApi.addAuraProblem(userId, problemRequest)
                    
                    withContext(Dispatchers.Main) {
                        if (problemResponse.isSuccessful) {
                            LogHelper.d("NoiseEffectManager: Level 4 effect and aura problem applied successfully")
                        } else {
                            LogHelper.e("NoiseEffectManager: Failed to apply level 4 aura problem: ${problemResponse.code()}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        LogHelper.e("NoiseEffectManager: Failed to apply level 4 effect: ${effectResponse.code()}")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error applying level 4 effect: ${e.message}")
            }
        }
    }
    
    private fun applyLevel5Effect(userId: String) {
        scope.launch {
            try {
                // Создаем эффект "Перегрузка нервной системы"
                val effectRequest = EffectRequest(
                    textToShowPlayers = LEVEL_5_EFFECT_TEXT,
                    timeToLiveInMinutes = 120,
                    mark = AuraMarkRequest(
                        markType = AuraMarkType.FOREIGN_PLANE_INFLUENCE,
                        imageUrl = "http://shift96.ru/static/images/noosfer_plane_influence.png",
                        name = "Влияние Ноосферы",
                        description = "Влияние Ноосферы",
                        external = false,
                        numberOfStars = 0
                    )
                )
                
                val effectResponse = RetrofitClient.effectApi.createEffect(userId, effectRequest)
                
                if (effectResponse.isSuccessful) {
                    // Добавляем проблему в ауру - Дыра
                    val problemRequest = AuraProblemRequest(
                        slot = null,
                        problemType = AuraProblemType.HOLE,
                        name = "Дыра",
                        description = "Получено от максимального уровня шума, аура заметно повредилась"
                    )
                    
                    val problemResponse = RetrofitClient.auraApi.addAuraProblem(userId, problemRequest)
                    
                    withContext(Dispatchers.Main) {
                        if (problemResponse.isSuccessful) {
                            LogHelper.d("NoiseEffectManager: Level 5 effect and aura problem applied successfully")
                        } else {
                            LogHelper.e("NoiseEffectManager: Failed to apply level 5 aura problem: ${problemResponse.code()}")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        LogHelper.e("NoiseEffectManager: Failed to apply level 5 effect: ${effectResponse.code()}")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error applying level 5 effect: ${e.message}")
            }
        }
    }
    
    /**
     * Проверяет, есть ли у пользователя Proxy эффект
     */
    fun hasProxyEffect(): Boolean {
        val userProfile = UserPrefsHelper.getUserData(context)
        return userProfile?.effects?.any { 
            it.textToShowPlayers == PROXY_EFFECT_TEXT
        } ?: false
    }
    
    /**
     * Применяет Proxy эффект пользователю
     */
    fun applyProxyEffect(userId: String) {
        scope.launch {
            try {
                LogHelper.d("NoiseEffectManager: Applying Proxy effect for user $userId")
                
                val effectRequest = EffectRequest(
                    textToShowPlayers = PROXY_EFFECT_TEXT,
                    timeToLiveInMinutes = 1440, // Proxy эффект активен 24 часа (24 * 60 минут)
                    mark = null // Proxy эффект не имеет метки
                )
                
                val effectResponse = RetrofitClient.effectApi.createEffect(userId, effectRequest)
                if (effectResponse.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        LogHelper.d("NoiseEffectManager: Proxy effect applied successfully")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        LogHelper.e("NoiseEffectManager: Failed to apply Proxy effect: ${effectResponse.code()}")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error applying Proxy effect: ${e.message}")
            }
        }
    }
    
    /**
     * Проверяет, есть ли у пользователя активная Cross-Link связь
     */
    fun hasCrossLinkEffect(): Boolean {
        val userProfile = UserPrefsHelper.getUserData(context)
        return userProfile?.effects?.any { effect ->
            effect.textToShowPlayers.matches(CROSS_LINK_EFFECT_PATTERN.toRegex())
        } ?: false
    }
    
    /**
     * Получает имя партнера из Cross-Link эффекта
     */
    fun getCrossLinkPartnerName(): String? {
        val userProfile = UserPrefsHelper.getUserData(context)
        val crossLinkEffect = userProfile?.effects?.find { effect ->
            effect.textToShowPlayers.matches(CROSS_LINK_EFFECT_PATTERN.toRegex())
        }
        
        return if (crossLinkEffect != null) {
            val regex = "Связь с (.+) установлена, шум делится пополам".toRegex()
            val matchResult = regex.find(crossLinkEffect.textToShowPlayers)
            matchResult?.groupValues?.get(1)
        } else null
    }
    
    /**
     * Применяет Cross-Link эффект для обоих пользователей
     */
    fun applyCrossLinkEffect(userId1: String, userId2: String, partnerName1: String, partnerName2: String) {
        scope.launch {
            try {
                // Создаем метку для Cross-Link связи
                val mark1 = AuraMarkRequest(
                    markType = AuraMarkType.MAGIC_LINK,
                    name = "Магическая связь позитивная",
                    description = "Связь с шумомантом $partnerName2 через модуль Cross-Vault",
                    imageUrl = "http://shift96.ru/static/images/magic_link_positive.png",
                    external = false,
                    numberOfStars = null
                )
                
                val mark2 = AuraMarkRequest(
                    markType = AuraMarkType.MAGIC_LINK,
                    name = "Магическая связь позитивная",
                    description = "Связь с шумомантом $partnerName1 через модуль Cross-Vault",
                    imageUrl = "http://shift96.ru/static/images/magic_link_positive.png",
                    external = false,
                    numberOfStars = null
                )
                
                // Создаем эффект для первого пользователя
                val effect1 = EffectRequest(
                    textToShowPlayers = "Связь с $partnerName2 установлена, шум делится пополам",
                    timeToLiveInMinutes = 1440, // Cross-Link связь активна 24 часа (24 * 60 минут)
                    mark = mark1
                )
                
                // Создаем эффект для второго пользователя
                val effect2 = EffectRequest(
                    textToShowPlayers = "Связь с $partnerName1 установлена, шум делится пополам",
                    timeToLiveInMinutes = 1440, // Cross-Link связь активна 24 часа (24 * 60 минут)
                    mark = mark2
                )
                
                // Применяем эффекты параллельно
                val response1 = RetrofitClient.effectApi.createEffect(userId1, effect1)
                val response2 = RetrofitClient.effectApi.createEffect(userId2, effect2)
                
                withContext(Dispatchers.Main) {
                    if (response1.isSuccessful && response2.isSuccessful) {
                        LogHelper.d("NoiseEffectManager: Cross-Link effects applied successfully")
                    } else {
                        LogHelper.e("NoiseEffectManager: Failed to apply Cross-Link effects: ${response1.code()}, ${response2.code()}")
                    }
                }
            } catch (e: Exception) {
                LogHelper.e("NoiseEffectManager: Error applying Cross-Link effects: ${e.message}")
            }
        }
    }
}
