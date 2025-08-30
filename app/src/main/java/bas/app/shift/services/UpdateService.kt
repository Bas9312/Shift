package bas.app.shift.services

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import android.content.pm.PackageManager
import bas.app.shift.helpers.LogHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

class UpdateService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    
    companion object {
        private const val UPDATE_JSON_URL = "https://shift96.ru/static/update.json"
        private const val UPDATE_APK_FILENAME = "update.apk"
    }
    
    // Переменная для хранения отложенного обновления
    private var pendingUpdateInfo: UpdateInfo? = null
    
    // Уникальный ID для отслеживания логов
    private val instanceId = System.currentTimeMillis() % 10000
    
    data class UpdateInfo(
        val latestVersion: String,
        val latestVersionCode: Int,
        val url: String,
        val releaseNotes: List<String>,
        val mandatoryMinVersion: Int,
        val sha256: String?
    )
    
    fun checkForUpdates(onUpdateAvailable: (UpdateInfo) -> Unit) {
        LogHelper.d("UpdateService[$instanceId]: Начинаем проверку обновлений")
        lifecycleOwner.lifecycleScope.launch {
            try {
                LogHelper.d("UpdateService[$instanceId]: Запускаем fetchUpdateInfo")
                val updateInfo = fetchUpdateInfo()
                LogHelper.d("UpdateService[$instanceId]: Получена информация об обновлении: $updateInfo")
                
                // Проверяем, есть ли новая версия
                val currentVersionCode = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                    }
                } catch (e: Exception) {
                    LogHelper.e("UpdateService[$instanceId]: Ошибка получения текущей версии: ${e.message}")
                    1 // fallback
                }
                
                LogHelper.d("UpdateService[$instanceId]: Текущая версия: $currentVersionCode, доступная версия: ${updateInfo.latestVersionCode}")
                
                if (currentVersionCode < updateInfo.latestVersionCode) {
                    LogHelper.i("UpdateService[$instanceId]: Доступно обновление! Показываем диалог")
                    withContext(Dispatchers.Main) {
                        onUpdateAvailable(updateInfo)
                    }
                } else {
                    LogHelper.d("UpdateService[$instanceId]: Обновление не требуется")
                }
            } catch (e: Exception) {
                LogHelper.e("UpdateService[$instanceId]: Критическая ошибка при проверке обновлений: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun fetchUpdateInfo(): UpdateInfo {
        return withContext(Dispatchers.IO) {
            LogHelper.d("UpdateService: Начинаем загрузку JSON с $UPDATE_JSON_URL")
            
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(UPDATE_JSON_URL)
                .build()
            
            LogHelper.d("UpdateService: Отправляем HTTP запрос")
            val response = client.newCall(request).execute()
            LogHelper.d("UpdateService: Получен ответ: код ${response.code}")
            
            if (!response.isSuccessful) {
                LogHelper.e("UpdateService: HTTP ошибка: ${response.code} ${response.message}")
                throw IOException("HTTP error: ${response.code}")
            }
            
            val jsonString = response.body?.string() ?: throw IOException("Empty response")
            LogHelper.d("UpdateService: Получен JSON: $jsonString")
            
            val json = JSONObject(jsonString)
            
            val updateInfo = UpdateInfo(
                latestVersion = json.getString("latestVersion"),
                latestVersionCode = json.getInt("latestVersionCode"),
                url = json.getString("url"),
                releaseNotes = json.getJSONArray("releaseNotes").let { array ->
                    List(array.length()) { array.getString(it) }
                },
                mandatoryMinVersion = json.optInt("mandatoryMinVersion", json.getInt("latestVersionCode")),
                sha256 = json.optString("sha256").takeIf { it != "PUT_YOUR_APK_SHA256" }
            )
            
            LogHelper.d("UpdateService: Парсинг JSON завершен: $updateInfo")
            updateInfo
        }
    }
    
    fun showUpdateDialog(updateInfo: UpdateInfo) {
        LogHelper.i("UpdateService: Показываем диалог обновления для версии ${updateInfo.latestVersion}")
        
        val releaseNotesText = if (updateInfo.releaseNotes.isNotEmpty()) {
            updateInfo.releaseNotes.joinToString("\n• ", "• ")
        } else {
            "Новая версия доступна"
        }
        
        val dialog = AlertDialog.Builder(context)
            .setTitle("Доступно обновление")
            .setMessage("Версия ${updateInfo.latestVersion}\n\n$releaseNotesText")
            .setPositiveButton("Обновить") { _, _ ->
                LogHelper.i("UpdateService: Пользователь нажал 'Обновить'")
                downloadAndInstallUpdate(updateInfo)
            }
            .setNegativeButton("Позже") { _, _ ->
                LogHelper.i("UpdateService: Пользователь отказался от обновления")
            }
            .setCancelable(false)
            .create()
        
        LogHelper.d("UpdateService: Диалог создан, показываем")
        dialog.show()
    }
    
    private fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        LogHelper.i("UpdateService: Начинаем процесс загрузки и установки обновления")
        LogHelper.d("UpdateService: URL для загрузки: ${updateInfo.url}")
        
        // Проверяем разрешение на установку из неизвестных источников
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
            !context.packageManager.canRequestPackageInstalls()) {
            
            LogHelper.w("UpdateService: Нет разрешения на установку из неизвестных источников")
            LogHelper.i("UpdateService: Показываем диалог запроса разрешения")
            
            // Показываем предупреждение перед открытием системного экрана
            AlertDialog.Builder(context)
                .setTitle("Разрешение на установку")
                .setMessage("Для установки обновления необходимо разрешить установку приложений из неизвестных источников. Перейти в настройки?")
                .setPositiveButton("Да") { _, _ ->
                    LogHelper.i("UpdateService: Пользователь согласился на переход в настройки")
                    // Сохраняем информацию об обновлении для продолжения после возвращения
                    pendingUpdateInfo = updateInfo
                    LogHelper.d("UpdateService: Сохранили pendingUpdateInfo: $updateInfo")
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    LogHelper.d("UpdateService: Открыли экран настроек разрешений")
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }
        
        LogHelper.d("UpdateService: Разрешение на установку есть, продолжаем")
        
        // Используем DownloadManager для загрузки APK
        LogHelper.d("UpdateService: Инициализируем DownloadManager")
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        LogHelper.d("UpdateService: Создаем запрос на загрузку")
        val request = DownloadManager.Request(Uri.parse(updateInfo.url))
            .setTitle("Загрузка обновления")
            .setDescription("Загружается версия ${updateInfo.latestVersion}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, UPDATE_APK_FILENAME)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        LogHelper.d("UpdateService: Отправляем запрос в DownloadManager")
        val downloadId = downloadManager.enqueue(request)
        LogHelper.i("UpdateService: Загрузка начата, ID: $downloadId")
        
        // Регистрируем BroadcastReceiver для отслеживания завершения загрузки
        LogHelper.d("UpdateService: Регистрируем BroadcastReceiver для отслеживания загрузки")
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                LogHelper.d("UpdateService: BroadcastReceiver получил intent: ${intent?.action}")
                
                if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
                    LogHelper.d("UpdateService: Неизвестное действие: ${intent?.action}")
                    return
                }
                
                val completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                LogHelper.d("UpdateService: Получен ID загрузки: $completedDownloadId, ожидаем: $downloadId")
                
                if (completedDownloadId != downloadId) {
                    LogHelper.d("UpdateService: ID не совпадает, игнорируем")
                    return
                }
                
                LogHelper.i("UpdateService: Загрузка завершена, проверяем статус")
                
                try {
                    context?.unregisterReceiver(this)
                    LogHelper.d("UpdateService: BroadcastReceiver отменен")
                } catch (e: Exception) {
                    LogHelper.w("UpdateService: Ошибка отмены receiver: ${e.message}")
                }
                
                // Проверяем статус загрузки
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(columnIndex)
                    LogHelper.d("UpdateService: Статус загрузки: $status")
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        LogHelper.i("UpdateService: Загрузка успешна, устанавливаем APK")
                        // Загрузка успешна, устанавливаем APK
                        val uri = downloadManager.getUriForDownloadedFile(downloadId)
                        if (uri != null) {
                            LogHelper.d("UpdateService: Получен URI: $uri")
                            installApk(uri)
                        } else {
                            LogHelper.w("UpdateService: URI не получен, используем fallback")
                            // Fallback: ищем файл по пути
                            val apkFile = File(this@UpdateService.context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_APK_FILENAME)
                            if (apkFile.exists()) {
                                LogHelper.d("UpdateService: APK найден по пути: ${apkFile.absolutePath}")
                                installApk(FileProvider.getUriForFile(
                                    this@UpdateService.context,
                                    "${this@UpdateService.context.packageName}.fileprovider",
                                    apkFile
                                ))
                            } else {
                                LogHelper.e("UpdateService: APK не найден по пути: ${apkFile.absolutePath}")
                                Toast.makeText(this@UpdateService.context, "Ошибка: файл обновления не найден", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        LogHelper.e("UpdateService: Ошибка загрузки, статус: $status")
                        Toast.makeText(this@UpdateService.context, "Ошибка загрузки обновления", Toast.LENGTH_LONG).show()
                    }
                } else {
                    LogHelper.e("UpdateService: Не удалось получить статус загрузки")
                }
                cursor.close()
            }
        }
        
        // Регистрируем receiver с правильным флагом и приоритетом
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE).apply {
            priority = 1000 // Высокий приоритет
        }
        context.registerReceiver(onComplete, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        
        // Добавляем таймер для проверки статуса загрузки
        lifecycleOwner.lifecycleScope.launch {
            delay(5000) // Ждем 5 секунд
            LogHelper.d("UpdateService: Проверяем статус загрузки через таймер")
            
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(columnIndex)
                LogHelper.d("UpdateService: Статус загрузки (таймер): $status")
                
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    LogHelper.i("UpdateService: Загрузка завершена (таймер), устанавливаем APK")
                    try {
                        context.unregisterReceiver(onComplete)
                    } catch (e: Exception) {
                        LogHelper.w("UpdateService: Ошибка отмены receiver (таймер): ${e.message}")
                    }
                    
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri != null) {
                        LogHelper.d("UpdateService: Получен URI (таймер): $uri")
                        installApk(uri)
                    } else {
                        LogHelper.w("UpdateService: URI не получен (таймер), используем fallback")
                        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), UPDATE_APK_FILENAME)
                        if (apkFile.exists()) {
                            LogHelper.d("UpdateService: APK найден по пути (таймер): ${apkFile.absolutePath}")
                            installApk(FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                apkFile
                            ))
                        } else {
                            LogHelper.e("UpdateService: APK не найден по пути (таймер): ${apkFile.absolutePath}")
                            Toast.makeText(context, "Ошибка: файл обновления не найден", Toast.LENGTH_LONG).show()
                        }
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    LogHelper.e("UpdateService: Загрузка не удалась (таймер), статус: $status")
                    Toast.makeText(context, "Ошибка загрузки обновления", Toast.LENGTH_LONG).show()
                } else {
                    LogHelper.w("UpdateService: Загрузка еще не завершена (таймер), статус: $status")
                }
            } else {
                LogHelper.e("UpdateService: Не удалось получить статус загрузки (таймер)")
            }
            cursor.close()
        }
    }
    
    private fun installApk(apkUri: Uri) {
        LogHelper.i("UpdateService: Начинаем установку APK: $apkUri")
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        LogHelper.d("UpdateService: Intent создан: $intent")
        
        try {
            context.startActivity(intent)
            LogHelper.i("UpdateService: Установщик APK запущен успешно")
        } catch (e: Exception) {
            LogHelper.e("UpdateService: Ошибка запуска установщика: ${e.message}")
            Toast.makeText(context, "Ошибка установки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Проверяет, есть ли отложенное обновление и можно ли его продолжить
     * Вызывается после возвращения из настроек разрешений
     */
    fun checkPendingUpdate() {
        LogHelper.d("UpdateService: Проверяем отложенное обновление")
        
        val pending = pendingUpdateInfo
        if (pending == null) {
            LogHelper.d("UpdateService: Отложенного обновления нет")
            return
        }
        
        LogHelper.d("UpdateService: Найдено отложенное обновление: $pending")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
            context.packageManager.canRequestPackageInstalls()) {
            LogHelper.i("UpdateService: Разрешение получено, продолжаем обновление")
            // Разрешение получено, продолжаем обновление
            pendingUpdateInfo = null
            downloadAndInstallUpdate(pending)
        } else {
            LogHelper.w("UpdateService: Разрешение на установку еще не получено")
        }
    }
}
