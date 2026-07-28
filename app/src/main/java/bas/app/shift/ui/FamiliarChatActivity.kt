package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityFamiliarChatBinding
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.ChatMessage
import bas.app.shift.models.ChatSendRequest
import bas.app.shift.ui.terminal.ChatAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class FamiliarChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityFamiliarChatBinding
    private lateinit var chatAdapter: ChatAdapter
    private var familiar: String = ""
    private var userId: String = ""

    /** Точка фамильяра на карте, если в чат пришли с неё. Нужна, чтобы держать привязку. */
    private var pointId: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFamiliarChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Получаем данные из intent
        pointId = intent.getStringExtra("point_id")
        familiar = intent.getStringExtra("familiar") ?: ""
        if (familiar.isEmpty()) {
            Toast.makeText(this, "Ошибка: не указан фамильяр", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Получаем userId из SharedPreferences
        userId = UserPrefsHelper.getUserId(this) ?: ""
        if (userId.isEmpty()) {
            Toast.makeText(this, "Ошибка: не найден ID пользователя", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        initViews()
        setupRecyclerView()
        loadChatHistory()
    }
    
    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.familiar_chat_title)
        
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.tvDisclaimer.setOnClickListener { showDisclaimerDialog() }
        showDisclaimerOnFirstOpen()
    }

    /**
     * Фамильяр — это ИИ: он может пообещать игроку что угодно (предмет, эффект, «иди туда»),
     * и прецеденты уже были. Полный текст показываем принудительно при первом открытии чата,
     * дальше он остаётся доступен по тапу на плашку, которая видна всегда.
     */
    private fun showDisclaimerOnFirstOpen() {
        val prefs = getSharedPreferences(DISCLAIMER_PREFS, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DISCLAIMER_SHOWN, false)) return
        showDisclaimerDialog()
        prefs.edit().putBoolean(KEY_DISCLAIMER_SHOWN, true).apply()
    }

    private fun showDisclaimerDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.familiar_chat_disclaimer_title)
            .setMessage(R.string.familiar_chat_disclaimer)
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun setupRecyclerView() {
        chatAdapter = ChatAdapter()
        binding.rvChat.layoutManager = LinearLayoutManager(this)
        binding.rvChat.adapter = chatAdapter
    }
    
    private fun loadChatHistory() {
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.chatApi.getChatHistory(userId, familiar)
                }
                
                if (response.isSuccessful) {
                    val chatHistory = response.body()
                    if (chatHistory != null) {
                        chatAdapter.updateMessages(chatHistory.messages)
                        scrollToBottom()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = when (response.code()) {
                        400 -> "Неверный запрос: ${errorBody ?: "неизвестная ошибка"}"
                        404 -> "Чат не найден"
                        else -> NetworkErrors.http(response.code())
                    }
                    Toast.makeText(this@FamiliarChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FamiliarChatActivity, NetworkErrors.network(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return
        
        // Очищаем поле ввода
        binding.etMessage.text?.clear()
        
        // Создаем сообщение пользователя
        val userMessage = ChatMessage(
            role = "user",
            content = text,
            ts = System.currentTimeMillis() / 1000.0
        )
        
        // Добавляем в чат
        chatAdapter.addMessage(userMessage)
        scrollToBottom()

        // Продлеваем захват фамильяра: 15 минут отсчитываются от последнего сообщения,
        // так что пока разговор идёт, чужие сюда не влезут.
        pointId?.let { bas.app.shift.services.ServerService.touchFamiliar(it) }

        // Отправляем на сервер
        lifecycleScope.launch {
            try {
                val request = ChatSendRequest(userId, familiar, text)
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.chatApi.sendMessage(request)
                }
                
                if (response.isSuccessful) {
                    val chatResponse = response.body()
                    if (chatResponse != null) {
                        // Добавляем ответ фамильяра
                        val assistantMessage = ChatMessage(
                            role = "assistant",
                            content = chatResponse.text,
                            ts = System.currentTimeMillis() / 1000.0
                        )
                        chatAdapter.addMessage(assistantMessage)
                        scrollToBottom()
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = when (response.code()) {
                        400 -> "Неверный запрос: ${errorBody ?: "неизвестная ошибка"}"
                        404 -> "Чат не найден"
                        else -> NetworkErrors.http(response.code())
                    }
                    Toast.makeText(this@FamiliarChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@FamiliarChatActivity, NetworkErrors.network(e), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun scrollToBottom() {
        binding.rvChat.post {
            if (chatAdapter.itemCount > 0) {
                binding.rvChat.smoothScrollToPosition(chatAdapter.itemCount - 1)
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private companion object {
        const val DISCLAIMER_PREFS = "familiar_chat_prefs"
        const val KEY_DISCLAIMER_SHOWN = "disclaimer_shown"
    }
}
