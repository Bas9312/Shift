package bas.app.shift.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import bas.app.shift.ui.MessagesChatActivity

class MessagesReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "bas.app.shift.NEW_MESSAGES" -> {
                val unreadCount = intent.getIntExtra("unread_count", 0)
                if (unreadCount > 0) {
                    showNotification(context, unreadCount)
                }
            }
        }
    }
    
    private fun showNotification(context: Context, unreadCount: Int) {
        val message = if (unreadCount == 1) {
            "У вас 1 новое сообщение"
        } else {
            "У вас $unreadCount новых сообщений"
        }
        
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
