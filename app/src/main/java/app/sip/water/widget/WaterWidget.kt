package app.sip.water.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import app.sip.water.Graph
import app.sip.water.MainActivity
import app.sip.water.R
import app.sip.water.data.Volume
import app.sip.water.reminders.ActionReceiver
import java.time.LocalDate

/**
 * Home-screen widget: today's progress and a one-tap button for your usual
 * amount, so logging a drink does not even need the app open.
 */
class WaterWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        render(context, manager, ids)
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, WaterWidget::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val s = Graph.settings.current
            val total = Graph.repository.totalForDay(LocalDate.now().toEpochDay())
            // Rounded, to match the percentage shown in the app.
            val pct = if (s.goalMl > 0) Math.round(total * 100f / s.goalMl).coerceIn(0, 100) else 0

            val views = RemoteViews(context.packageName, R.layout.widget_water).apply {
                setTextViewText(R.id.widget_amount, Volume.format(total, s.unit))
                setTextViewText(
                    R.id.widget_goal,
                    if (total >= s.goalMl) "Goal reached · ${Volume.format(s.goalMl, s.unit)}"
                    else "of ${Volume.format(s.goalMl, s.unit)} · $pct%",
                )
                setProgressBar(R.id.widget_progress, 100, pct, false)
                setTextViewText(R.id.widget_add, "+ ${Volume.format(s.lastAmountMl, s.unit)}")

                // No amount in the intent: the button always adds whatever you logged last.
                setOnClickPendingIntent(
                    R.id.widget_add,
                    PendingIntent.getBroadcast(
                        context,
                        10,
                        Intent(context, ActionReceiver::class.java).setAction(ActionReceiver.ACTION_ADD),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        11,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
