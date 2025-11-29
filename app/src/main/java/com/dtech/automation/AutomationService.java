package com.dtech.automation;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.content.pm.ServiceInfo;
import androidx.core.app.NotificationCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import com.dtech.automation.worker.DailyReminderWorker;

public class AutomationService extends Service {

    private final IBinder binder = new LocalBinder();
    private PowerManager.WakeLock wakeLock;
    private static final String CHANNEL_ID = "AutomationServiceChannel";
    private UrlManager urlManager;
    private List<UrlManager.UrlItem> queue = new ArrayList<>();
    private UrlManager.UrlItem currentItem;
    private ServiceCallback callback;
    private boolean isRunning = false;
    private Handler timeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable timeoutRunnable = this::onReplayFailed;

    public interface ServiceCallback {
        void loadUrl(String url);
        void injectScript(String overrideValue);
        void onQueueFinished();
    }

    public class LocalBinder extends Binder {
        AutomationService getService() {
            return AutomationService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        urlManager = new UrlManager(this);
        createNotificationChannel();

        // Acquire WakeLock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DTech:AutomationWakeLock");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : "";
        if ("STOP".equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Start Foreground immediately
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(1, createNotification("Initializing...", 0, 0), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, createNotification("Initializing...", 0, 0));
        }

        // Acquire lock
        if (wakeLock != null && !wakeLock.isHeld()) {
            wakeLock.acquire(10*60*1000L /*10 mins limit*/);
        }

        return START_NOT_STICKY;
    }

    public void setCallback(ServiceCallback callback) {
        this.callback = callback;
    }

    public void startBatchProcess() {
        if (isRunning) return;
        isRunning = true;

        // Build Queue
        queue.clear();
        List<UrlManager.UrlItem> all = urlManager.getUrls();
        for (UrlManager.UrlItem item : all) {
            if (item.isDue()) {
                queue.add(item);
            }
        }

        if (queue.isEmpty()) {
            stopSelf(); // Nothing to do
            if (callback != null) callback.onQueueFinished();
            return;
        }

        processNext();
    }

    private void processNext() {
        if (queue.isEmpty()) {
            finishBatch();
            return;
        }

        currentItem = queue.remove(0);
        updateNotification("Processing: " + currentItem.url, 0, 0); // Todo: Add progress count

        // Reset timeout
        timeoutHandler.removeCallbacks(timeoutRunnable);

        if (callback != null) {
            callback.loadUrl("https://leofame.com/free-tiktok-views");
            // Note: We always load the TARGET SITE, but we inject the currentItem.url as the INPUT value.
        }
    }

    // Called by Activity when page finishes loading
    public void onPageReady() {
        if (!isRunning || currentItem == null) return;

        if (callback != null) {
            // Inject with override value
            callback.injectScript(currentItem.url);

            // Start Timeout (5 minutes)
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutHandler.postDelayed(timeoutRunnable, 5 * 60 * 1000);
        }
    }

    // Called by Activity when Replay reports finished
    public void onReplayFinished() {
        if (!isRunning || currentItem == null) return;

        timeoutHandler.removeCallbacks(timeoutRunnable);

        // Success logic: Reset retry and mark done
        urlManager.updateLastRun(currentItem.url);

        // Next
        processNext();
    }

    // Called if failure detected (e.g. timeout) - Placeholder for future robust timeout logic
    public void onReplayFailed() {
        if (!isRunning || currentItem == null) return;

        if (currentItem.retryCount < 2) {
             currentItem.retryCount++;
             queue.add(0, currentItem); // Retry immediately
             updateNotification("Retrying: " + currentItem.url + " (" + currentItem.retryCount + "/2)", 0, 0);
        } else {
             // Too many retries, skip
             // We do NOT update lastRun so it might run again tomorrow or we can choose to mark it?
             // User said "give up n try tomorrow", so we can assume we don't block it forever but skip for now.
             // If we don't update timestamp, it remains "due". If we want to skip until tomorrow:
             urlManager.updateLastRun(currentItem.url);
        }
        processNext();
    }

    private void finishBatch() {
        isRunning = false;
        updateNotification("Batch Complete!", 0, 0);
        if (callback != null) callback.onQueueFinished();

        // Release lock
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        scheduleNextDayReminder();

        stopSelf();
    }

    private void scheduleNextDayReminder() {
        OneTimeWorkRequest reminderRequest = new OneTimeWorkRequest.Builder(DailyReminderWorker.class)
                .setInitialDelay(24, TimeUnit.HOURS)
                .build();
        WorkManager.getInstance(this).enqueue(reminderRequest);
    }

    private void updateNotification(String text, int progress, int max) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(1, createNotification(text, progress, max));
        }
    }

    private Notification createNotification(String text, int progress, int max) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("D-TECH Automation")
                .setContentText(text)
                .setSmallIcon(R.mipmap.ic_launcher) // Ensure this exists
                .setContentIntent(pendingIntent);

        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Automation Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }
}
