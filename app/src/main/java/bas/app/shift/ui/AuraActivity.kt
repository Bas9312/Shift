package bas.app.shift.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import bas.app.shift.R
import bas.app.shift.api.AuraApi
import bas.app.shift.api.RetrofitClient
import bas.app.shift.helpers.AuraCleanupManager
import bas.app.shift.helpers.LogHelper
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.models.AuraMark
import bas.app.shift.models.AuraProblem
import bas.app.shift.models.AuraProblemRequest
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraActivity : AppCompatActivity(), AuraMarkCallback {
    private lateinit var auraApi: AuraApi
    private lateinit var auraCanvas: AuraCanvasView

    /** id существа, чью ауру смотрим — приходит из QR-сканера. Он же ключ состояния чистки. */
    private var entityId: String? = null

    /** Последняя загруженная аура: нужна, чтобы перед применением сверить, что проблема не сменилась. */
    private var currentProblems: List<AuraProblem> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aura)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.title = "Аура"
        toolbar.setNavigationOnClickListener {
            finish()
        }
        auraApi = RetrofitClient.auraApi
        auraCanvas = findViewById(R.id.auraCanvas)

        // Устанавливаем callback для обработки long tap
        auraCanvas.markCallback = this

        // Получаем ID ауры из intent
        val auraId = intent.getStringExtra("aura_id")
        if (auraId != null) {
            entityId = auraId
            loadAura(auraId)
        } else {
            Toast.makeText(this, "Не указан ID ауры", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadAura(auraId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = auraApi.getAura(auraId)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val aura = response.body()
                    if (aura != null) {
                        currentProblems = aura.auraProblems ?: emptyList()
                        auraCanvas.setAura(aura)
                    } else {
                        Toast.makeText(this@AuraActivity, "Ошибка: пустая аура", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this@AuraActivity, "Ошибка загрузки ауры", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onMarkLongTap(mark: AuraMark) {
        Toast.makeText(this, "Метка: ${mark.name}", Toast.LENGTH_SHORT).show()
    }

    override fun onProblemLongTap(slot: Int, problem: AuraProblem?) {
        val id = entityId
        if (problem == null || id == null) {
            Toast.makeText(this, getString(R.string.aura_cleanup_empty_slot, slot), Toast.LENGTH_SHORT).show()
            return
        }

        val progress = AuraCleanupManager.progress(this, id, slot)
        when {
            progress != null -> showCleanupProgressDialog(id, slot, problem, progress)
            AuraCleanupManager.canClean(problem.problemType) -> showCleanupStartDialog(id, slot, problem)
            else -> showMasterOnlyDialog(problem)
        }
    }

    /** Дыра, паразит и «другое» экстрасенсом не снимаются — только через мастера. */
    private fun showMasterOnlyDialog(problem: AuraProblem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.aura_cleanup_title)
            .setMessage(getString(R.string.aura_cleanup_master_only, problem.name))
            .setPositiveButton("Понятно", null)
            .show()
    }

    private fun showCleanupStartDialog(entityId: String, slot: Int, problem: AuraProblem) {
        val minutes = AuraCleanupManager.durationMinutes(problem.problemType) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.aura_cleanup_title)
            .setMessage(getString(R.string.aura_cleanup_start_question, problem.name, minutes))
            .setPositiveButton(R.string.aura_cleanup_start_button) { _, _ ->
                AuraCleanupManager.start(
                    context = this,
                    entityId = entityId,
                    slot = slot,
                    type = problem.problemType,
                    now = System.currentTimeMillis()
                )
                Toast.makeText(
                    this,
                    getString(R.string.aura_cleanup_in_progress, problem.name, "$minutes:00"),
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCleanupProgressDialog(
        entityId: String,
        slot: Int,
        problem: AuraProblem,
        progress: AuraCleanupManager.Progress,
    ) {
        val now = System.currentTimeMillis()
        val builder = AlertDialog.Builder(this).setTitle(R.string.aura_cleanup_title)

        if (progress.isReady(now)) {
            val outcomeText = when (val outcome = AuraCleanupManager.outcomeFor(progress.problemType)) {
                is AuraCleanupManager.Outcome.Removed ->
                    getString(R.string.aura_cleanup_result_removed)
                is AuraCleanupManager.Outcome.Converted ->
                    getString(R.string.aura_cleanup_result_converted, outcome.toName)
                null -> ""
            }
            builder.setMessage(getString(R.string.aura_cleanup_ready, problem.name, outcomeText))
                .setPositiveButton(R.string.aura_cleanup_confirm_button) { _, _ ->
                    applyCleanup(entityId, slot, progress)
                }
        } else {
            val remaining = AuraCleanupManager.formatRemaining(progress.remainingMs(now))
            builder.setMessage(getString(R.string.aura_cleanup_in_progress, problem.name, remaining))
                .setPositiveButton("Закрыть", null)
        }

        builder.setNegativeButton(R.string.aura_cleanup_cancel_button) { _, _ ->
            AuraCleanupManager.cancel(this, entityId, slot)
        }
        builder.show()
    }

    /**
     * Применяет результат чистки на сервере. Перед записью сверяем, что в слоте всё ещё
     * та же проблема: пока шли 15 минут, мастер мог поменять ауру руками, и молча затереть
     * его правку было бы хуже, чем попросить начать заново.
     */
    private fun applyCleanup(entityId: String, slot: Int, progress: AuraCleanupManager.Progress) {
        val actual = currentProblems.find { it.slot == slot }
        if (actual == null || actual.problemType != progress.problemType) {
            AuraCleanupManager.cancel(this, entityId, slot)
            Toast.makeText(this, R.string.aura_cleanup_problem_changed, Toast.LENGTH_LONG).show()
            LogHelper.w("AuraCleanup: слот $slot изменился (${actual?.problemType} вместо ${progress.problemType}), чистка отменена")
            return
        }

        val outcome = AuraCleanupManager.outcomeFor(progress.problemType) ?: return

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    when (outcome) {
                        is AuraCleanupManager.Outcome.Removed ->
                            auraApi.deleteAuraProblem(entityId, slot)
                        is AuraCleanupManager.Outcome.Converted ->
                            auraApi.updateAuraProblem(
                                entityId,
                                slot,
                                AuraProblemRequest(
                                    slot = slot,
                                    problemType = outcome.toType,
                                    name = outcome.toName,
                                    description = "Затянулось после чистки ауры"
                                )
                            )
                    }
                }

                if (response.isSuccessful) {
                    // Состояние снимаем только после успешной записи: если сервер не ответил,
                    // экстрасенс сможет нажать «Подтвердить» ещё раз, не выжидая срок заново.
                    AuraCleanupManager.cancel(this@AuraActivity, entityId, slot)
                    Toast.makeText(this@AuraActivity, R.string.aura_cleanup_applied, Toast.LENGTH_SHORT).show()
                    LogHelper.d("AuraCleanup: применено для $entityId slot=$slot, результат=$outcome")
                    loadAura(entityId)
                } else {
                    Toast.makeText(
                        this@AuraActivity,
                        getString(R.string.aura_cleanup_error, NetworkErrors.http(response.code())),
                        Toast.LENGTH_LONG
                    ).show()
                    LogHelper.e("AuraCleanup: сервер вернул ${response.code()}")
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@AuraActivity,
                    getString(R.string.aura_cleanup_error, NetworkErrors.network(e)),
                    Toast.LENGTH_LONG
                ).show()
                LogHelper.e("AuraCleanup: исключение при применении: ${e.message}")
            }
        }
    }
}
