package bas.app.shift.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import bas.app.shift.R
import bas.app.shift.api.AuraApi
import bas.app.shift.api.RetrofitClient
import bas.app.shift.databinding.FragmentAuraBinding
import bas.app.shift.models.Aura
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AuraFragment : Fragment() {
    private var _binding: FragmentAuraBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var auraApi: AuraApi
    private var currentUserId: String? = null

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
    }

    private fun setupUI() {
        // Настройка UI элементов если понадобится
    }

    fun loadUserAura(userId: String) {
        currentUserId = userId
        loadAura(userId)
    }

    private fun loadAura(userId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = auraApi.getAura(userId)
            withContext(Dispatchers.Main) {
                if (response.isSuccessful) {
                    val aura = response.body()
                    if (aura != null) {
                        binding.auraCanvas.setAura(aura)
                        showAuraLoaded()
                    } else {
                        showError("Ошибка: пустая аура")
                    }
                } else {
                    showError("Ошибка загрузки ауры: ${response.code()}")
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
