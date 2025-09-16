package bas.app.shift.ui

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import bas.app.shift.R
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.ActivityMessagesChatBinding
import bas.app.shift.databinding.DialogRecipientSelectionBinding
import bas.app.shift.databinding.DialogTagsSelectionBinding
import bas.app.shift.databinding.DialogDisciplineSelectionBinding
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.*
import bas.app.shift.ui.adapters.MessagesAdapter
import bas.app.shift.ui.adapters.DisciplinesAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class MessagesChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMessagesChatBinding
    private lateinit var messagesAdapter: MessagesAdapter
    private var userId: String = ""
    private var selectedFiles: MutableList<Uri> = mutableListOf()
    private var selectedRecipient: String = ""
    private var selectedTags: MutableList<Int> = mutableListOf()
    private var pollingJob: Job? = null
    private val pollingScope = CoroutineScope(Dispatchers.Main)
    private var lastMessageId: Int = -1
    private var currentTempId: Int = -1
    private var pendingMessageText: String = ""
    private var pendingFiles: MutableList<Uri> = mutableListOf()
    
    companion object {
        private const val REQUEST_CODE_PICK_FILES = 1001
        private const val REQUEST_CODE_PERMISSIONS = 1002
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMessagesChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Получаем userId из SharedPreferences
        userId = UserPrefsHelper.getUserId(this) ?: ""
        if (userId.isEmpty()) {
            Toast.makeText(this, "Ошибка: не найден ID пользователя", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // Получаем реципиента из Intent или определяем по логике
        selectedRecipient = intent.getStringExtra("recipient_id") ?: run {
            if (userId.startsWith("MG_")) {
                // Для МГ пользователей - если нет реципиента, перенаправляем на список чатов
                Toast.makeText(this, "Выберите чат из списка", Toast.LENGTH_SHORT).show()
                finish()
                return
            } else {
                // Для обычных пользователей - всегда МГ
                "MG_Bas"
            }
        }
        
        android.util.Log.d("MessagesChat", "Initialized: userId=$userId, selectedRecipient=$selectedRecipient")
        
        // Обновляем заголовок если есть имя реципиента
        val recipientName = intent.getStringExtra("recipient_name")
        if (!recipientName.isNullOrEmpty()) {
            supportActionBar?.title = "Чат с $recipientName"
        }
        
        initViews()
        setupRecyclerView()
        loadMessages()
        
        // Запускаем периодическое обновление только для обычных пользователей
        if (!userId.startsWith("MG_")) {
            startPolling()
        }
    }
    
    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Чат с МГ"
        
        binding.btnSend.setOnClickListener {
            sendMessage()
        }
        
        binding.btnAttach.setOnClickListener {
            checkPermissionsAndPickFiles()
        }
    }
    
    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter { message ->
            // Обработка клика по сообщению
            markMessageAsRead(message)
        }
        messagesAdapter.setCurrentUserId(userId)
        
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            // Переворачиваем RecyclerView чтобы новые сообщения были снизу
            stackFromEnd = true
        }
        binding.rvMessages.adapter = messagesAdapter
    }
    
    private fun loadMessages(showLoader: Boolean = true) {
        if (showLoader) {
            showLoading(true)
        }
        
        val call = if (userId.startsWith("MG_")) {
            // Для МГ пользователей используем новый API истории чата
            android.util.Log.d("MessagesChat", "Loading chat history: userId=$userId, peerId=$selectedRecipient")
            RetrofitClient.messagesApi.getChatHistory(
                userId = userId,
                peerId = selectedRecipient,
                limit = 100,
                offset = 0
            )
        } else {
            // Для обычных пользователей используем старый API
            RetrofitClient.messagesApi.getMessages(
                userId = userId,
                limit = 50,
                offset = 0,
                type = "private"
            )
        }
        
        call.enqueue(object : retrofit2.Callback<List<Message>> {
            override fun onResponse(call: retrofit2.Call<List<Message>>, response: retrofit2.Response<List<Message>>) {
                if (response.isSuccessful) {
                    val messages = response.body() ?: emptyList()
                    
                    if (showLoader) {
                        // Первая загрузка - переворачиваем порядок сообщений (от старых к новым)
                        val reversedMessages = messages.reversed()
                        messagesAdapter.updateMessages(reversedMessages)
                        if (messages.isNotEmpty()) {
                            lastMessageId = messages.maxByOrNull { it.id }?.id ?: -1
                        }
                    } else {
                        // Периодическое обновление - добавляем только новые сообщения
                        val newMessages = messages.filter { it.id > lastMessageId }
                        if (newMessages.isNotEmpty()) {
                            newMessages.forEach { message ->
                                // Проверяем, что сообщение еще не добавлено (избегаем дублирования)
                                if (!messagesAdapter.hasMessage(message.id)) {
                                    messagesAdapter.addMessage(message)
                                }
                            }
                            lastMessageId = newMessages.maxByOrNull { it.id }?.id ?: lastMessageId
                            scrollToBottom()
                        }
                    }
                    
                    if (showLoader) {
                        scrollToBottom()
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        401 -> "Ошибка авторизации"
                        400 -> "Неверный запрос"
                        500 -> "Ошибка сервера"
                        else -> "Ошибка загрузки сообщений: ${response.code()}"
                    }
                    Toast.makeText(this@MessagesChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
                if (showLoader) {
                    showLoading(false)
                }
            }
            
            override fun onFailure(call: retrofit2.Call<List<Message>>, t: Throwable) {
                val errorMessage = when {
                    t.message?.contains("UnknownHostException") == true -> "Ошибка сети: сервер недоступен"
                    t.message?.contains("SocketTimeoutException") == true -> "Ошибка сети: превышено время ожидания"
                    else -> "Ошибка: ${t.message ?: "неизвестная ошибка"}"
                }
                if (showLoader) {
                    Toast.makeText(this@MessagesChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
                if (showLoader) {
                    showLoading(false)
                }
            }
        })
    }
    
    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty() && selectedFiles.isEmpty()) {
            Toast.makeText(this, "Введите сообщение или выберите файл", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Проверяем, является ли пользователь МГ
        if (!userId.startsWith("MG_")) {
            // Для не-МГ пользователей показываем диалог выбора дисциплины
            showDisciplineSelectionDialog(text, selectedFiles.toList())
            return
        }
        
        // Для МГ пользователей отправляем сразу
        sendMessageWithTags(text, selectedFiles.toList(), selectedTags)
    }
    
    private fun sendMessageWithTags(text: String, files: List<Uri>, tags: List<Int>) {
        // Очищаем поле ввода
        binding.etMessage.text?.clear()
        
        // Создаем временное сообщение для отображения
        currentTempId = System.currentTimeMillis().toInt() // Уникальный временный ID
        val tempMessage = Message(
            id = currentTempId,
            senderId = userId,
            recipientId = selectedRecipient,
            content = text,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            readStatus = "unread",
            tags = tags
        )
        
        messagesAdapter.addMessage(tempMessage)
        scrollToBottom()
        
        // Отправляем на сервер
        val textBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
        val recipientBody = selectedRecipient.toRequestBody("text/plain".toMediaTypeOrNull())
        
        val parts = mutableListOf<MultipartBody.Part>()
        parts.add(MultipartBody.Part.createFormData("text", text))
        parts.add(MultipartBody.Part.createFormData("recipient_id", selectedRecipient))
        
        // Добавляем теги, если они есть
        if (tags.isNotEmpty()) {
            val tagsString = tags.joinToString(",")
            parts.add(MultipartBody.Part.createFormData("tags", tagsString))
        }
        
        // Добавляем файлы, если они есть
        files.forEach { uri ->
            val file = File(uri.path ?: "")
            if (file.exists()) {
                val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("files", file.name, requestFile)
                parts.add(part)
            }
        }
        
        RetrofitClient.messagesApi.createMessage(
            userId = userId,
            text = textBody,
            recipientId = recipientBody,
            tags = null,
            files = parts
        ).enqueue(object : retrofit2.Callback<CreateMessageResponse> {
            override fun onResponse(call: retrofit2.Call<CreateMessageResponse>, response: retrofit2.Response<CreateMessageResponse>) {
                if (response.isSuccessful) {
                    val createdMessage = response.body()
                    if (createdMessage != null) {
                        // Заменяем временное сообщение на реальное
                        val realMessage = Message(
                            id = createdMessage.id,
                            senderId = createdMessage.senderId,
                            recipientId = createdMessage.recipientId,
                            content = createdMessage.content,
                            createdAt = createdMessage.createdAt,
                            readStatus = createdMessage.readStatus,
                            tags = createdMessage.tags
                        )
                        
                        // Удаляем временное сообщение и добавляем реальное
                        messagesAdapter.removeMessage(currentTempId)
                        messagesAdapter.addMessage(realMessage)
                        
                        // Обновляем lastMessageId
                        lastMessageId = createdMessage.id
                    }
                } else {
                    val errorMessage = when (response.code()) {
                        400 -> "Неверный запрос"
                        401 -> "Ошибка авторизации"
                        404 -> "Получатель не найден"
                        500 -> "Ошибка сервера"
                        else -> "Ошибка отправки: ${response.code()}"
                    }
                    Toast.makeText(this@MessagesChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
                selectedFiles.clear()
            }
            
            override fun onFailure(call: retrofit2.Call<CreateMessageResponse>, t: Throwable) {
                val errorMessage = when {
                    t.message?.contains("UnknownHostException") == true -> "Ошибка сети: сервер недоступен"
                    t.message?.contains("SocketTimeoutException") == true -> "Ошибка сети: превышено время ожидания"
                    else -> "Ошибка: ${t.message ?: "неизвестная ошибка"}"
                }
                Toast.makeText(this@MessagesChatActivity, errorMessage, Toast.LENGTH_LONG).show()
                selectedFiles.clear()
            }
        })
    }
    
    private fun showDisciplineSelectionDialog(text: String, files: List<Uri>) {
        val disciplineNames = Disciplines.DISCIPLINES.map { it.name }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Выберите дисциплину")
            .setItems(disciplineNames) { _, which ->
                val selectedDiscipline = Disciplines.DISCIPLINES[which]
                android.util.Log.d("DisciplineDialog", "Selected: ${selectedDiscipline.name} (ID: ${selectedDiscipline.id})")
                // Отправляем сообщение с выбранной дисциплиной как тегом
                sendMessageWithTags(text, files, listOf(selectedDiscipline.id))
            }
            .setNegativeButton("Отмена", null)
            .show()
    }
    
    private fun markMessageAsRead(message: Message) {
        if (message.readStatus == "unread" && message.senderId != userId) {
            RetrofitClient.messagesApi.markAsRead(userId, message.id)
                .enqueue(object : retrofit2.Callback<MarkAsReadResponse> {
                    override fun onResponse(call: retrofit2.Call<MarkAsReadResponse>, response: retrofit2.Response<MarkAsReadResponse>) {
                        if (response.isSuccessful) {
                            val updatedMessage = message.copy(readStatus = "read")
                            messagesAdapter.updateMessage(updatedMessage)
                        }
                    }
                    
                    override fun onFailure(call: retrofit2.Call<MarkAsReadResponse>, t: Throwable) {
                        // Игнорируем ошибки при пометке как прочитанное
                    }
                })
        }
    }
    
    private fun checkPermissionsAndPickFiles() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            pickFiles()
        }
    }
    
    private fun pickFiles() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "image/*,video/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(Intent.createChooser(intent, "Выберите файлы"), REQUEST_CODE_PICK_FILES)
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickFiles()
            } else {
                Toast.makeText(this, "Разрешение на доступ к файлам необходимо для прикрепления", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILES && resultCode == Activity.RESULT_OK) {
            data?.let { intent ->
                if (intent.clipData != null) {
                    // Множественный выбор
                    val count = intent.clipData!!.itemCount
                    for (i in 0 until count) {
                        val uri = intent.clipData!!.getItemAt(i).uri
                        selectedFiles.add(uri)
                    }
                } else if (intent.data != null) {
                    // Одиночный выбор
                    selectedFiles.add(intent.data!!)
                }
                
                if (selectedFiles.isNotEmpty()) {
                    Toast.makeText(this, "Выбрано файлов: ${selectedFiles.size}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun scrollToBottom() {
        binding.rvMessages.post {
            if (messagesAdapter.itemCount > 0) {
                // При stackFromEnd = true, последний элемент находится в позиции 0
                // Но теперь новые сообщения добавляются в конец, поэтому скроллим к последней позиции
                binding.rvMessages.smoothScrollToPosition(messagesAdapter.itemCount - 1)
            }
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    private fun startPolling() {
        pollingJob = pollingScope.launch {
            while (true) {
                delay(10000) // 10 секунд
                loadMessages(showLoader = false) // Без лоадера для периодических обновлений
            }
        }
    }
    
    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopPolling()
    }
    
    override fun onPause() {
        super.onPause()
        // Останавливаем polling только для обычных пользователей
        if (!userId.startsWith("MG_")) {
            stopPolling()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Запускаем polling только для обычных пользователей
        if (!userId.startsWith("MG_")) {
            startPolling()
        }
    }
}
