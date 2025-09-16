package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityChatsListBinding
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.Chat
import bas.app.shift.ui.adapters.ChatsAdapter
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

class ChatsListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatsListBinding
    private lateinit var adapter: ChatsAdapter
    private var userId: String? = null
    private var refreshHandler: Handler? = null
    private var refreshRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        loadChats()
        startPeriodicRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPeriodicRefresh()
    }

    override fun onResume() {
        super.onResume()
        startPeriodicRefresh()
    }

    override fun onPause() {
        super.onPause()
        stopPeriodicRefresh()
    }

    private fun startPeriodicRefresh() {
        refreshHandler = Handler(Looper.getMainLooper())
        refreshRunnable = object : Runnable {
            override fun run() {
                loadChats()
                refreshHandler?.postDelayed(this, 30000) // 30 секунд
            }
        }
        refreshHandler?.post(refreshRunnable!!)
    }

    private fun stopPeriodicRefresh() {
        refreshHandler?.removeCallbacks(refreshRunnable!!)
        refreshHandler = null
        refreshRunnable = null
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Чаты"
        
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatsAdapter { chat ->
            openChat(chat)
        }
        
        binding.recyclerViewChats.apply {
            layoutManager = LinearLayoutManager(this@ChatsListActivity)
            adapter = this@ChatsListActivity.adapter
        }
    }

    private fun loadChats() {
        userId = UserPrefsHelper.getUserId(this)
        if (userId.isNullOrEmpty()) {
            showError("Ошибка: не удалось получить ID пользователя")
            return
        }

        showLoading(true)
        
        RetrofitClient.messagesApi.getChats(userId!!)
            .enqueue(object : Callback<List<Chat>> {
                override fun onResponse(call: Call<List<Chat>>, response: Response<List<Chat>>) {
                    showLoading(false)
                    
                    if (response.isSuccessful && response.body() != null) {
                        val chats = response.body()!!
                        if (chats.isEmpty()) {
                            showEmptyState(true)
                        } else {
                            showEmptyState(false)
                            adapter.updateChats(chats)
                        }
                    } else {
                        showError("Ошибка загрузки чатов: ${response.code()}")
                    }
                }

                override fun onFailure(call: Call<List<Chat>>, t: Throwable) {
                    showLoading(false)
                    showError("Ошибка сети: ${t.message}")
                    LogHelper.e("ChatsListActivity: Error loading chats: ${t.message}")
                }
            })
    }

    private fun openChat(chat: Chat) {
        val intent = Intent(this, MessagesChatActivity::class.java).apply {
            putExtra("recipient_id", chat.interlocutor)
            // Формируем имя в том же формате, что и в списке чатов
            val displayName = when {
                !chat.interlocutorName.isNullOrEmpty() && !chat.interlocutorPlayerName.isNullOrEmpty() -> 
                    "${chat.interlocutorName} / ${chat.interlocutorPlayerName}"
                !chat.interlocutorName.isNullOrEmpty() -> 
                    chat.interlocutorName
                !chat.interlocutorPlayerName.isNullOrEmpty() -> 
                    chat.interlocutorPlayerName
                else -> 
                    chat.interlocutor
            }
            putExtra("recipient_name", displayName)
        }
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyState(show: Boolean) {
        binding.layoutEmpty.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        LogHelper.e("ChatsListActivity: $message")
    }
}
