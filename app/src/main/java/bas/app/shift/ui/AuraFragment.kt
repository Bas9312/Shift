package bas.app.shift.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import bas.app.shift.R
import bas.app.shift.api.AuraApi
import bas.app.shift.ui.AuraMarkCallback
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.FragmentAuraBinding
import bas.app.shift.helpers.NetworkErrors
import bas.app.shift.models.Aura
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraFragment : Fragment() {
    private var _binding: FragmentAuraBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var auraApi: AuraApi
    private var currentUserId: String? = null
    private var markCallback: AuraMarkCallback? = null
    private var auraEditorCallback: AuraEditorCallback? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAuraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        auraApi = RetrofitClient.auraApi
        setupUI()
        
        // Если callback уже установлен, применяем его к canvas
        applyCallbackIfReady()
    }

    private fun setupUI() {
        // Настройка UI элементов если понадобится
    }

    fun loadUserAura(userId: String) {
        currentUserId = userId
        // Убеждаемся, что callback установлен
        applyCallbackIfReady()
        loadAura(userId)
    }
    
    fun setMarkCallback(callback: AuraMarkCallback) {
        this.markCallback = callback
        // Проверяем, что binding готов и применяем callback
        applyCallbackIfReady()
    }
    
    fun setAuraEditorCallback(callback: AuraEditorCallback) {
        this.auraEditorCallback = callback
    }
    
    private fun applyCallbackIfReady() {
        if (_binding != null && markCallback != null) {
            binding.auraCanvas.markCallback = markCallback
        }
    }

    private fun loadAura(userId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val response = auraApi.getAura(userId)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val aura = response.body()
                    if (aura != null) {
                        binding.auraCanvas.setAura(aura)
                        showAuraLoaded()
                        // Уведомляем о загрузке ауры
                        auraEditorCallback?.onAuraLoaded(aura)
                    } else {
                        showError("Ошибка: пустая аура")
                    }
                } else {
                    showError(NetworkErrors.http(response.code()))
                }
            }
        }
    }

    private fun showAuraLoaded() {
        // Можно добавить уведомление об успешной загрузке
    }

    private fun showError(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun clearAura() {
        binding.auraCanvas.setAura(null)
        currentUserId = null
        // Убеждаемся, что callback установлен
        applyCallbackIfReady()
    }
    
    fun setAuraVisibility(visible: Boolean?) {
        binding.auraCanvas.setAuraVisibility(visible)
    }
    
    fun setEditorMode(isEditor: Boolean) {
        binding.auraCanvas.setEditorMode(isEditor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
