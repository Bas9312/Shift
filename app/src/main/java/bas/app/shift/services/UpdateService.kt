package bas.app.shift.services

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import android.content.pm.PackageManager
import bas.app.shift.helpers.LogHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class UpdateService(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val updateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    companion object {
        private const val UPDATE_JSON_URL = "https://shift96.ru/static/update.json"
        private const val LEGACY_UPDATE_APK_FILENAME = "update.apk"
        private const val UPDATE_APK_PREFIX = "shift-update-"
        internal const val INSTALL_STATUS_ACTION = "bas.app.shift.UPDATE_INSTALL_STATUS"

        private const val UPDATE_PREFS = "update_state"
        private const val KEY_SNOOZED_VERSION = "snoozed_version_code"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until"
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_DOWNLOAD_VERSION = "download_version"
        private const val KEY_DOWNLOAD_VERSION_CODE = "download_version_code"
        private const val KEY_DOWNLOAD_URL = "download_url"
        private const val KEY_DOWNLOAD_SHA256 = "download_sha256"

        /** Сколько молчать после «Позже». Игра идёт четыре дня — два часа это один-два раза за день. */
        private const val SNOOZE_DURATION_MS = 2 * 60 * 60 * 1000L

        private val updateInProgress = AtomicBoolean(false)

        internal fun markInstallationFinished() {
            updateInProgress.set(false)
        }
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
    
    /**
     * Отложить предложение об этой версии. Главный экран спрашивает сервер каждые полчаса,
     * и без отсрочки отказ «Позже» означал бы тот же диалог через тридцать минут — в поле,
     * посреди игры. Отсрочка привязана к версии: следующая версия спросит сразу.
     */
    private fun snooze(versionCode: Int) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_SNOOZED_VERSION, versionCode)
            .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + SNOOZE_DURATION_MS)
            .apply()
        LogHelper.d("UpdateService: Версия $versionCode отложена на ${SNOOZE_DURATION_MS / 60000} мин")
    }

    /** true, если игрок уже сказал «Позже» про эту версию и отсрочка ещё не истекла. */
    private fun isSnoozed(versionCode: Int): Boolean {
        val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY_SNOOZED_VERSION, -1) != versionCode) return false
        return System.currentTimeMillis() < prefs.getLong(KEY_SNOOZED_UNTIL, 0L)
    }

    /**
     * DownloadManager продолжает качать APK и после уничтожения Activity, но динамический
     * receiver Activity при этом снимается. Запоминаем ровно столько, сколько нужно, чтобы
     * следующая Activity смогла подхватить завершившуюся загрузку или дождаться её опросом.
     * Состояние убирается сразу после commit PackageInstaller: дальше APK уже принадлежит
     * системной установочной сессии, а не DownloadManager.
     */
    private fun savePendingDownload(downloadId: Long, updateInfo: UpdateInfo) {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_DOWNLOAD_VERSION, updateInfo.latestVersion)
            .putInt(KEY_DOWNLOAD_VERSION_CODE, updateInfo.latestVersionCode)
            .putString(KEY_DOWNLOAD_URL, updateInfo.url)
            .putString(KEY_DOWNLOAD_SHA256, updateInfo.sha256)
            .apply()
    }

    private fun pendingDownload(): Pair<Long, UpdateInfo>? {
        val prefs = context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE)
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        val version = prefs.getString(KEY_DOWNLOAD_VERSION, null)
        val versionCode = prefs.getInt(KEY_DOWNLOAD_VERSION_CODE, -1)
        val url = prefs.getString(KEY_DOWNLOAD_URL, null)
        if (id < 0 || version.isNullOrBlank() || versionCode < 0 || url.isNullOrBlank()) return null
        return id to UpdateInfo(
            latestVersion = version,
            latestVersionCode = versionCode,
            url = url,
            releaseNotes = emptyList(),
            mandatoryMinVersion = versionCode,
            sha256 = prefs.getString(KEY_DOWNLOAD_SHA256, null)
        )
    }

    private fun clearPendingDownload() {
        context.getSharedPreferences(UPDATE_PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_DOWNLOAD_ID)
            .remove(KEY_DOWNLOAD_VERSION)
            .remove(KEY_DOWNLOAD_VERSION_CODE)
            .remove(KEY_DOWNLOAD_URL)
            .remove(KEY_DOWNLOAD_SHA256)
            .apply()
    }

    fun showUpdateDialog(updateInfo: UpdateInfo) {
        if (isSnoozed(updateInfo.latestVersionCode)) {
            LogHelper.d("UpdateService: Версия ${updateInfo.latestVersion} отложена игроком, диалог не показываем")
            return
        }

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
                snooze(updateInfo.latestVersionCode)
            }
            .setCancelable(false)
            .create()
        
        LogHelper.d("UpdateService: Диалог создан, показываем")
        dialog.show()
    }
    
    private fun downloadAndInstallUpdate(updateInfo: UpdateInfo) {
        LogHelper.i("UpdateService: Начинаем процесс загрузки и установки обновления")
        LogHelper.d("UpdateService: URL для загрузки: ${updateInfo.url}")

        if (!updateInProgress.compareAndSet(false, true)) {
            LogHelper.w("UpdateService: Загрузка или установка обновления уже запущена")
            Toast.makeText(context, "Обновление уже загружается или устанавливается", Toast.LENGTH_SHORT).show()
            return
        }
        
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
                    // Открываем разрешение именно для Shift, а не общий список приложений.
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    LogHelper.d("UpdateService: Открыли экран настроек разрешений")
                }
                .setNegativeButton("Отмена", null)
                .show()
            updateInProgress.set(false)
            return
        }
        
        LogHelper.d("UpdateService: Разрешение на установку есть, продолжаем")
        
        // Используем DownloadManager для загрузки APK
        LogHelper.d("UpdateService: Инициализируем DownloadManager")
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadDirectory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (downloadDirectory == null) {
            LogHelper.e("UpdateService: Каталог загрузок приложения недоступен")
            Toast.makeText(context, "Ошибка: хранилище недоступно", Toast.LENGTH_LONG).show()
            updateInProgress.set(false)
            return
        }
        val apkFilename = "$UPDATE_APK_PREFIX${updateInfo.latestVersionCode}.apk"
        val apkFile = File(downloadDirectory, apkFilename)
        val downloadStartedAt = SystemClock.elapsedRealtime()
        LogHelper.i("UpdateService: Версия ${updateInfo.latestVersion} (code ${updateInfo.latestVersionCode}), файл=$apkFilename")

        // DownloadManager не перезаписывает назначенный файл. Удаляем только APK нашего
        // обновлятора из закрытого каталога приложения, чтобы повторная попытка была чистой.
        downloadDirectory.listFiles()?.filter { file ->
            file.name == LEGACY_UPDATE_APK_FILENAME ||
                (file.name.startsWith(UPDATE_APK_PREFIX) && file.name.endsWith(".apk"))
        }?.forEach { file ->
            if (!file.delete()) {
                LogHelper.w("UpdateService: Не удалось удалить старый APK: ${file.name}")
            }
        }
        
        LogHelper.d("UpdateService: Создаем запрос на загрузку")
        val request = DownloadManager.Request(Uri.parse(updateInfo.url))
            .setTitle("Загрузка обновления")
            .setDescription("Загружается версия ${updateInfo.latestVersion}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, apkFilename)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        LogHelper.d("UpdateService: Отправляем запрос в DownloadManager")
        val downloadId = downloadManager.enqueue(request)
        savePendingDownload(downloadId, updateInfo)
        LogHelper.i("UpdateService: Загрузка начата, ID: $downloadId")
        
        // Регистрируем BroadcastReceiver для отслеживания завершения загрузки
        LogHelper.d("UpdateService: Регистрируем BroadcastReceiver для отслеживания загрузки")
        // Receiver и таймер-фоллбек ниже могут оба увидеть STATUS_SUCCESSFUL почти одновременно —
        // этот флаг гарантирует, что установка/тост об ошибке запускается только один раз.
        val downloadHandled = AtomicBoolean(false)
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

                if (!downloadHandled.compareAndSet(false, true)) {
                    LogHelper.d("UpdateService: Статус уже обработан таймером, пропускаем receiver")
                    return
                }

                // Проверяем статус загрузки
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(columnIndex)
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else -1
                    LogHelper.i("UpdateService: DownloadManager завершил загрузку: status=$status, reason=$reason, bytes=${apkFile.length()}, elapsedMs=${SystemClock.elapsedRealtime() - downloadStartedAt}")
                    
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        updateScope.launch {
                            verifyAndInstall(updateInfo, apkFile)
                        }
                    } else {
                        LogHelper.e("UpdateService: Ошибка загрузки, статус: $status")
                        clearPendingDownload()
                        Toast.makeText(this@UpdateService.context, "Ошибка загрузки обновления", Toast.LENGTH_LONG).show()
                        updateInProgress.set(false)
                    }
                } else {
                    LogHelper.e("UpdateService: Не удалось получить статус загрузки")
                    clearPendingDownload()
                    updateInProgress.set(false)
                }
                cursor.close()
            }
        }
        
        // Регистрируем receiver с правильным флагом и приоритетом
        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE).apply {
            priority = 1000 // Высокий приоритет
        }
        context.registerReceiver(onComplete, intentFilter, Context.RECEIVER_NOT_EXPORTED)

        // Страховка от утечки receiver'а: если экран уничтожен до завершения загрузки
        // (пользователь закрыл приложение на медленной сети), receiver снимается здесь —
        // иначе он остаётся зарегистрированным на context уничтоженной Activity до тех пор,
        // пока DownloadManager не пришлёт broadcast (может не прийти вовсе, если процесс убьют).
        val destroyObserver = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (downloadHandled.compareAndSet(false, true)) {
                    updateInProgress.set(false)
                    try {
                        context.unregisterReceiver(onComplete)
                        LogHelper.d("UpdateService: BroadcastReceiver отменен при уничтожении экрана")
                    } catch (e: Exception) {
                        LogHelper.w("UpdateService: Ошибка отмены receiver при onDestroy: ${e.message}")
                    }
                }
                owner.lifecycle.removeObserver(this)
            }
        }
        lifecycleOwner.lifecycle.addObserver(destroyObserver)

        // Добавляем таймер для проверки статуса загрузки
        lifecycleOwner.lifecycleScope.launch {
            delay(5000) // Ждем 5 секунд
            LogHelper.d("UpdateService: Проверяем статус загрузки через таймер")
            
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = cursor.getInt(columnIndex)
                LogHelper.i("UpdateService: Проверка загрузки таймером: status=$status, bytes=${apkFile.length()}, elapsedMs=${SystemClock.elapsedRealtime() - downloadStartedAt}")
                
                if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                    // Терминальный статус: это тот же результат, что мог заметить receiver —
                    // гейтим общим флагом, чтобы установка/тост об ошибке не сработали дважды.
                    if (!downloadHandled.compareAndSet(false, true)) {
                        LogHelper.d("UpdateService: Статус уже обработан receiver-ом, пропускаем таймер")
                        cursor.close()
                        return@launch
                    }
                    try {
                        context.unregisterReceiver(onComplete)
                    } catch (e: Exception) {
                        LogHelper.w("UpdateService: Ошибка отмены receiver (таймер): ${e.message}")
                    }

                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        LogHelper.i("UpdateService: Загрузка завершена (таймер), проверяем APK")
                        updateScope.launch {
                            verifyAndInstall(updateInfo, apkFile)
                        }
                    } else {
                        LogHelper.e("UpdateService: Загрузка не удалась (таймер), статус: $status")
                        clearPendingDownload()
                        Toast.makeText(context, "Ошибка загрузки обновления", Toast.LENGTH_LONG).show()
                        updateInProgress.set(false)
                    }
                } else {
                    LogHelper.w("UpdateService: Загрузка еще не завершена (таймер), статус: $status")
                }
            } else {
                LogHelper.e("UpdateService: Не удалось получить статус загрузки (таймер)")
                clearPendingDownload()
                updateInProgress.set(false)
            }
            cursor.close()
        }
    }
    
    private suspend fun verifyAndInstall(updateInfo: UpdateInfo, apkFile: File) {
        val error = withContext(Dispatchers.IO) {
            if (!apkFile.isFile || apkFile.length() == 0L) {
                LogHelper.e("UpdateService: APK отсутствует или пуст: ${apkFile.absolutePath}")
                return@withContext "файл обновления не найден"
            }

            LogHelper.d("UpdateService: Проверяем APK ${apkFile.name}, размер=${apkFile.length()} байт")

            val expectedHash = updateInfo.sha256
                ?.replace(":", "")
                ?.lowercase()
                ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }

            if (expectedHash == null) {
                LogHelper.w("UpdateService: SHA-256 отсутствует или имеет неверный формат")
                return@withContext "сервер не передал контрольную сумму обновления"
            }

            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualHash = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            if (actualHash != expectedHash) {
                LogHelper.e("UpdateService: SHA-256 не совпал: expected=$expectedHash actual=$actualHash")
                apkFile.delete()
                "загруженный файл повреждён"
            } else {
                null
            }
        }

        if (error != null) {
            clearPendingDownload()
            Toast.makeText(context, "Ошибка: $error. Попробуйте ещё раз", Toast.LENGTH_LONG).show()
            updateInProgress.set(false)
            return
        }

        LogHelper.i("UpdateService: APK скачан полностью, SHA-256 подтверждён: ${apkFile.length()} байт")
        installApk(apkFile, updateInfo.url)
    }

    private suspend fun installApk(apkFile: File, originatingUrl: String) {
        LogHelper.i("UpdateService: Передаем APK в PackageInstaller.Session, размер=${apkFile.length()} байт")
        try {
            withContext(Dispatchers.IO) {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    setSize(apkFile.length())
                    setOriginatingUri(Uri.parse(originatingUrl))
                }
                val sessionId = installer.createSession(params)

                try {
                    installer.openSession(sessionId).use { session ->
                        apkFile.inputStream().buffered().use { input ->
                            session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                                input.copyTo(output)
                                session.fsync(output)
                            }
                        }

                        val statusIntent = Intent(context, UpdateInstallReceiver::class.java).apply {
                            action = INSTALL_STATUS_ACTION
                        }
                        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                        val statusPendingIntent = PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            statusIntent,
                            flags
                        )
                        session.commit(statusPendingIntent.intentSender)
                        // После commit файл уже в системной сессии. Повторный подхват
                        // DownloadManager при следующем старте мог бы открыть второй диалог.
                        clearPendingDownload()
                        LogHelper.i("UpdateService: Сессия установки $sessionId зафиксирована, ожидаем callback")
                    }
                } catch (e: Exception) {
                    installer.abandonSession(sessionId)
                    throw e
                }
            }
        } catch (e: Exception) {
            clearPendingDownload()
            updateInProgress.set(false)
            LogHelper.e("UpdateService: Ошибка передачи APK установщику: ${e.message}")
            Toast.makeText(context, "Ошибка установки: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Проверяет, есть ли отложенное обновление и можно ли его продолжить
     * Вызывается после возвращения из настроек разрешений
     */
    fun checkPendingUpdate() {
        LogHelper.d("UpdateService: Проверяем отложенное обновление")

        // Сначала восстанавливаем реальную загрузку. Это отдельный путь от разрешения
        // «неизвестные источники»: DownloadManager переживает Activity и процесс, а поле
        // pendingUpdateInfo — нет.
        val savedDownload = pendingDownload()
        if (savedDownload != null) {
            resumePendingDownload(savedDownload.first, savedDownload.second)
            return
        }
        
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

    private fun resumePendingDownload(downloadId: Long, updateInfo: UpdateInfo) {
        if (!updateInProgress.compareAndSet(false, true)) {
            LogHelper.d("UpdateService: Сохранённая загрузка уже обрабатывается")
            return
        }

        val directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val apkFile = directory?.let { File(it, "$UPDATE_APK_PREFIX${updateInfo.latestVersionCode}.apk") }
        if (apkFile == null) {
            LogHelper.e("UpdateService: Не удалось открыть каталог сохранённой загрузки")
            clearPendingDownload()
            updateInProgress.set(false)
            return
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // Ожидание принадлежит экрану: при следующем пересоздании его подхватит новый
        // UpdateService из сохранённого DownloadManager id. Саму установку, когда APK уже
        // готов, запускаем ниже в независимом scope, чтобы не оборвать её поворотом экрана.
        lifecycleOwner.lifecycleScope.launch {
            while (true) {
                val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
                val status = cursor.use {
                    if (!it.moveToFirst()) null
                    else it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                }

                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        LogHelper.i("UpdateService: Подхватили завершённую загрузку $downloadId")
                        updateScope.launch {
                            verifyAndInstall(updateInfo, apkFile)
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED, null -> {
                        LogHelper.w("UpdateService: Сохранённая загрузка $downloadId не завершилась, status=$status")
                        clearPendingDownload()
                        updateInProgress.set(false)
                        return@launch
                    }
                    else -> {
                        LogHelper.d("UpdateService: Ждём сохранённую загрузку $downloadId, status=$status")
                        delay(5_000)
                    }
                }
            }
        }
    }
}
