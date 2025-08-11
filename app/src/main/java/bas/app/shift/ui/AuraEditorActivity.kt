package bas.app.shift.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.databinding.ActivityAuraEditorBinding

class AuraEditorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAuraEditorBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuraEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
        
        // TODO: Реализовать редактор ауры для МГ
        binding.contentText.text = "Редактор Ауры для МГ\n\nЗдесь будет функционал для редактирования ауры персонажей"
    }
}
