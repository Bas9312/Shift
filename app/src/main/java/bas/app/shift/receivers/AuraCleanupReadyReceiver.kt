package bas.app.shift.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import bas.app.shift.helpers.AuraCleanupManager
import bas.app.shift.helpers.LogHelper
import bas.app.shift.services.LocationNotifications

/**
 * Fires when an aura cleanup's timer runs out, scheduled by [AuraCleanupManager] via
 * AlarmManager so the player gets a notification even if AuraActivity isn't open. Re-checks
 * the cleanup is still pending before notifying: the player may have already confirmed or
 * cancelled it while the app was in the foreground.
 */
class AuraCleanupReadyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID) ?: return
        val slot = intent.getIntExtra(EXTRA_SLOT, -1)
        if (slot < 0) return

        val progress = AuraCleanupManager.progress(context, entityId, slot)
        val now = System.currentTimeMillis()
        if (progress == null || !progress.isReady(now)) {
            LogHelper.d("AuraCleanupReadyReceiver: чистка $entityId slot=$slot уже не актуальна, уведомление не показываю")
            return
        }

        LocationNotifications(context).showAuraCleanupReadyNotification(entityId, slot)
    }

    companion object {
        const val EXTRA_ENTITY_ID = "entity_id"
        const val EXTRA_SLOT = "slot"
    }
}
