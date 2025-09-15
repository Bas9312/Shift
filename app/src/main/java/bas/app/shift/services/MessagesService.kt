package bas.app.shift.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.Message
import kotlinx.coroutines.*

class MessagesService : Service() {
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false
    
    companion object {
        private const val TAG = "MessagesService"
        private const val POLLING_INTERVAL = 10000L // 10 секунд
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            startPolling()
        }
        return START_STICKY
    }
    
    private fun startPolling() {
        isRunning = true
        serviceScope.launch {
            while (isRunning) {
                try {
                    checkForNewMessages()
                } catch (e: Exception) {
                    Log.e(TAG, "Ошибка при проверке сообщений", e)
                }
                delay(POLLING_INTERVAL)
            }
        }
    }
    
    private fun checkForNewMessages() {
        val userId = UserPrefsHelper.getUserId(this@MessagesService)
        if (userId.isNullOrEmpty()) return
        
        try {
            val response = RetrofitClient.messagesApi.getMessages(
                userId = userId,
                limit = 10,
                offset = 0,
                type = "private"
            )
            
            response.enqueue(object : retrofit2.Callback<List<Message>> {
                override fun onResponse(call: retrofit2.Call<List<Message>>, response: retrofit2.Response<List<Message>>) {
                    if (response.isSuccessful) {
                        val messages = response.body() ?: emptyList()
                        val unreadCount = messages.count { it.readStatus == "unread" && it.senderId != userId }
                        
                        if (unreadCount > 0) {
                            // Отправляем broadcast о новых сообщениях
                            val intent = Intent("bas.app.shift.NEW_MESSAGES")
                            intent.putExtra("unread_count", unreadCount)
                            sendBroadcast(intent)
                        }
                    }
                }
                
                override fun onFailure(call: retrofit2.Call<List<Message>>, t: Throwable) {
                    Log.e(TAG, "Ошибка при получении сообщений", t)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка при получении сообщений", e)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }
}
