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
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.helpers.UserPrefsHelper
import bas.app.shift.models.*
import bas.app.shift.ui.adapters.MessagesAdapter
import bas.app.shift.ui.adapters.DisciplinesAdapter
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var lastMessageId: Int = -1
    private var selectedMessageForReply: Message? = null
    private var currentTempId: Int = -1
    // Убывающий счётчик отрицательных временных id (реальные id сервера — положительные)
    private var nextTempId: Int = -1
    private var pendingMessageText: String = ""
    private var pendingFiles: MutableList<Uri> = mutableListOf()
    private var interlocutorName: String? = null
    private var isScreenActive = false
    
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
        android.util.Log.d("MessagesChat", "userId starts with MG_: ${userId.startsWith("MG_")}")
        
        // Обновляем заголовок если есть имя реципиента
        val recipientName = intent.getStringExtra("recipient_name")
        android.util.Log.d("MessagesChat", "recipient_name from intent: $recipientName")
        if (!recipientName.isNullOrEmpty()) {
            supportActionBar?.title = "Чат с $recipientName"
            interlocutorName = recipientName
            android.util.Log.d("MessagesChat", "interlocutorName set to: $interlocutorName")
        }
        
        initViews()
        setupRecyclerView()
        loadMessages()
        
        // Запускаем периодическое обновление для всех пользователей
        startPolling()
    }
    
    private fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Чат с МГ"
        
        binding.btnSend.setOnClickListener {
            sendMessage()
        }

        binding.btnMarkRead.setOnClickListener {
            markSelectedMessageAsRead()
        }
        
        binding.btnAttach.setOnClickListener {
            checkPermissionsAndPickFiles()
        }
        
        // Инициализируем состояние кнопки отправки
        updateSendButtonState()
    }
    
    private fun selectMessageForReply(message: Message) {
        android.util.Log.d("MessagesChat", "selectMessageForReply called for message: ${message.id}")
        selectedMessageForReply = message
        messagesAdapter.selectMessage(message.id)
        updateSendButtonState()
        
        // Показываем уведомление о выборе сообщения
        android.widget.Toast.makeText(
            this, 
            "Выбрано сообщение для ответа. Тег: ${getDisciplineName(message.tags.firstOrNull())}", 
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
    
    private fun clearMessageSelection() {
        selectedMessageForReply = null
        messagesAdapter.clearSelection()
        updateSendButtonState()
    }
    
    private fun updateSendButtonState() {
        if (userId.startsWith("MG_")) {
            // Для МГ пользователей кнопки видны только при выбранном сообщении
            val isVisible = selectedMessageForReply != null
            binding.btnSend.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
            binding.btnSend.isEnabled = isVisible
            binding.btnMarkRead.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
            binding.btnMarkRead.isEnabled = isVisible
        } else {
            // Для обычных пользователей кнопка всегда видна и активна
            binding.btnSend.visibility = android.view.View.VISIBLE
            binding.btnSend.isEnabled = true
            binding.btnSend.alpha = 1.0f
            binding.btnMarkRead.visibility = android.view.View.GONE
        }
    }
    
    private fun markSelectedMessageAsRead() {
        if (!userId.startsWith("MG_")) return

        val message = selectedMessageForReply
        if (message == null) {
            Toast.makeText(this, "Выберите сообщение (длинное нажатие)", Toast.LENGTH_SHORT).show()
            return
        }

        if (message.readStatus == "read") {
            clearMessageSelection()
            Toast.makeText(this, "Сообщение уже прочитано", Toast.LENGTH_SHORT).show()
            return
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Отметить прочитанным?")
            .setMessage("Сообщение будет отмечено прочитанным без ответа.")
            .setPositiveButton("Отметить") { _, _ ->
                RetrofitClient.messagesApi.markAsRead(userId = userId, messageId = message.id)
                    .enqueue(object : retrofit2.Callback<MarkAsReadResponse> {
                        override fun onResponse(
                            call: retrofit2.Call<MarkAsReadResponse>,
                            response: retrofit2.Response<MarkAsReadResponse>
                        ) {
                            if (response.isSuccessful) {
                                val updated = message.copy(readStatus = "read")
                                messagesAdapter.updateMessage(updated)
                                clearMessageSelection()
                                Toast.makeText(this@MessagesChatActivity, "Отмечено прочитанным", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(
                                    this@MessagesChatActivity,
                                    "Ошибка: ${response.code()}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }

                        override fun onFailure(call: retrofit2.Call<MarkAsReadResponse>, t: Throwable) {
                            Toast.makeText(
                                this@MessagesChatActivity,
                                "Ошибка сети: ${t.message ?: "неизвестная"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    })
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun getDisciplineName(tagId: Int?): String {
        return tagId?.let { id ->
            Disciplines.DISCIPLINES.find { it.id == id }?.name ?: "Неизвестная дисциплина"
        } ?: "Без тега"
    }
    
    private fun setupRecyclerView() {
        messagesAdapter = MessagesAdapter(
            onMessageClick = { message ->
                // Обработка клика по сообщению
            },
            onMessageLongClick = { message ->
                // Обработка длинного клика по сообщению (только для МГ)
                if (userId.startsWith("MG_")) {
                    selectMessageForReply(message)
                }
            }
        )
        messagesAdapter.setCurrentUserId(userId)
        messagesAdapter.setInterlocutorName(interlocutorName)
        messagesAdapter.setInterlocutorId(selectedRecipient)
        android.util.Log.d("MessagesChat", "Adapter configured with interlocutorName: $interlocutorName, interlocutorId: $selectedRecipient")
        
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
                    Toast.makeText(this@MessagesChatActivity, NetworkErrors.http(response.code()), Toast.LENGTH_LONG).show()
                }
                if (showLoader) {
                    showLoading(false)
                }
            }
            
            override fun onFailure(call: retrofit2.Call<List<Message>>, t: Throwable) {
                if (showLoader) {
                    Toast.makeText(this@MessagesChatActivity, NetworkErrors.network(t), Toast.LENGTH_LONG).show()
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
        
        // Для МГ пользователей проверяем, выбрано ли сообщение для ответа
        if (selectedMessageForReply == null) {
            Toast.makeText(this, "Выберите сообщение для ответа (длинное нажатие)", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Используем теги из выбранного сообщения
        val replyTags = selectedMessageForReply?.tags ?: emptyList()
        sendMessageWithTags(text, selectedFiles.toList(), replyTags)
    }
    
    private fun sendMessageWithTags(text: String, files: List<Uri>, tags: List<Int>) {
        android.util.Log.d("MessagesChat", "sendMessageWithTags: textLen=${text.length}, files=${files.size}, tags=$tags")

        // Очищаем поле ввода
        binding.etMessage.text?.clear()

        // Временный id — отрицательный и убывающий: гарантированно не совпадёт с реальными
        // (положительными) id сервера и не пересечётся между быстрыми повторными отправками.
        currentTempId = nextTempId--
        val tempId = currentTempId
        val replyMessage = selectedMessageForReply
        val tempMessage = Message(
            id = tempId,
            senderId = userId,
            recipientId = selectedRecipient,
            content = text,
            createdAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            readStatus = "unread",
            tags = tags
        )

        messagesAdapter.addMessage(tempMessage)
        scrollToBottom()

        // Текстовые поля передаём как отдельные @Part-параметры (без дублирования их же
        // внутри списка files, как было раньше — это слало серверу по два поля text/recipient_id
        // и при этом терялись tags/answer_to).
        val textBody = text.toRequestBody("text/plain".toMediaTypeOrNull())
        val recipientBody = selectedRecipient.toRequestBody("text/plain".toMediaTypeOrNull())
        val tagsBody = if (tags.isNotEmpty()) tags.joinToString(",").toRequestBody("text/plain".toMediaTypeOrNull()) else null
        val answerToBody = replyMessage?.id?.takeIf { it > 0 }?.toString()?.toRequestBody("text/plain".toMediaTypeOrNull())

        // Чтение вложений (крупное фото читается целиком в память) выносим на IO-поток,
        // чтобы не блокировать UI и не ловить ANR/OOM на главном потоке.
        lifecycleScope.launch(Dispatchers.IO) {
            val fileParts = mutableListOf<MultipartBody.Part>()
            files.forEach { uri ->
                try {
                    val mimeType = contentResolver.getType(uri) ?: "image/*"
                    val fileName = getFileName(uri) ?: "file_${System.currentTimeMillis()}"
                    contentResolver.openInputStream(uri)?.use { input ->
                        val requestFile = input.readBytes().toRequestBody(mimeType.toMediaTypeOrNull())
                        fileParts.add(MultipartBody.Part.createFormData("files", fileName, requestFile))
                    } ?: android.util.Log.e("MessagesChat", "Could not open input stream for URI: $uri")
                } catch (e: Exception) {
                    android.util.Log.e("MessagesChat", "Error processing file: ${e.message}")
                }
            }

            withContext(Dispatchers.Main) {
                sendCreateMessageRequest(textBody, recipientBody, tagsBody, answerToBody, fileParts, tempId, replyMessage)
            }
        }
    }

    private fun sendCreateMessageRequest(
        textBody: okhttp3.RequestBody,
        recipientBody: okhttp3.RequestBody,
        tagsBody: okhttp3.RequestBody?,
        answerToBody: okhttp3.RequestBody?,
        fileParts: List<MultipartBody.Part>,
        tempId: Int,
        replyMessage: Message?
    ) {
        RetrofitClient.messagesApi.createMessage(
            userId = userId,
            text = textBody,
            recipientId = recipientBody,
            tags = tagsBody,
            answerTo = answerToBody,
            files = fileParts
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

                        messagesAdapter.removeMessage(tempId)
                        messagesAdapter.addMessage(realMessage)
                        lastMessageId = createdMessage.id

                        // Помечаем исходное сообщение (на которое отвечали) как прочитанное
                        if (replyMessage != null) {
                            messagesAdapter.updateMessage(replyMessage.copy(readStatus = "read"))
                        }

                        clearMessageSelection()
                        loadMessages(false)
                    }
                } else {
                    Toast.makeText(this@MessagesChatActivity, NetworkErrors.http(response.code()), Toast.LENGTH_LONG).show()
                }
                selectedFiles.clear()
            }

            override fun onFailure(call: retrofit2.Call<CreateMessageResponse>, t: Throwable) {
                Toast.makeText(this@MessagesChatActivity, NetworkErrors.network(t), Toast.LENGTH_LONG).show()
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
    
    private fun checkPermissionsAndPickFiles() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_CODE_PERMISSIONS)
        } else {
            pickFiles()
        }
    }
    
    private fun pickFiles() {
        // Сначала пробуем ACTION_PICK с MediaStore
        val pickIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        
        // Альтернативный способ через ACTION_GET_CONTENT
        val getContentIntent = Intent(Intent.ACTION_GET_CONTENT)
        getContentIntent.type = "image/*"
        getContentIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        getContentIntent.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"))
        
        // Создаем chooser с обоими вариантами
        val chooserIntent = Intent.createChooser(pickIntent, "Выберите изображения")
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(getContentIntent))
        
        startActivityForResult(chooserIntent, REQUEST_CODE_PICK_FILES)
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
        if (requestCode == REQUEST_CODE_PICK_FILES) {
            if (resultCode == Activity.RESULT_OK) {
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
                    } else {
                        Toast.makeText(this, "Файлы не выбраны", Toast.LENGTH_SHORT).show()
                    }
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Выбор файлов отменен", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        var fileName: String? = null
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
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
        if (pollingJob?.isActive == true) return
        // lifecycleScope сам отменит корутину при уничтожении экрана.
        // delay стоит в НАЧАЛЕ каждой итерации — цикл всегда приостанавливается и никогда
        // не крутится вхолостую на главном потоке (раньше при isScreenActive=false был busy-loop).
        pollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(30000) // 30 секунд между обновлениями
                if (isScreenActive) {
                    loadMessages(showLoader = false) // Без лоадера для периодических обновлений
                }
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
        // Останавливаем polling для всех пользователей
        isScreenActive = false
        stopPolling()
    }
    
    override fun onResume() {
        super.onResume()
        // Запускаем polling для всех пользователей
        isScreenActive = true
        startPolling()
    }
}
