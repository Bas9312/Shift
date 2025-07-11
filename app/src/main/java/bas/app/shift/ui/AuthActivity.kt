package bas.app.shift.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import bas.app.shift.MainActivity
import bas.app.shift.R
import bas.app.shift.helpers.UserPrefsHelper

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val savedUserId = UserPrefsHelper.getUserId(this)
        if (savedUserId.isNotEmpty()) {
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            return
        }
        setContentView(R.layout.activity_auth)

        val editTextUserId = findViewById<EditText>(R.id.editTextUserId)
        val buttonLogin = findViewById<Button>(R.id.buttonLogin)

        buttonLogin.setOnClickListener {
            val userId = editTextUserId.text.toString().trim()
            if (userId.isEmpty()) {
                Toast.makeText(this, "Пожалуйста, введите Id", Toast.LENGTH_SHORT).show()
            } else {
                UserPrefsHelper.setUserId(this, userId)
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }
} 