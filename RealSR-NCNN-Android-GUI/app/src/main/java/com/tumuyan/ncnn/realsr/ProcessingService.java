package com.tumuyan.ncnn.realsr;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.tumuyan.ncnn.realsr.ui.MediaExportHelper;

import java.io.File;

public class ProcessingService extends Service {
    private static final String TAG = "ProcessingService";
    private static final String CHANNEL_ID_PROGRESS = "channel_progress";
    private static final String CHANNEL_ID_ALIVE = "channel_service_alive";
    private static final String CHANNEL_ID_RESULT = "channel_result";
    private static final int NOTIFICATION_ID = 1;
    private static final int RESULT_NOTIFICATION_BASE_ID = 2000;

    public static final String ACTION_STOP_TASK = "com.tumuyan.ncnn.realsr.ACTION_STOP_TASK";
    public static final String EXTRA_OPEN_CREATION_ID = "com.tumuyan.ncnn.realsr.EXTRA_OPEN_CREATION_ID";

    private final IBinder binder = new LocalBinder();
    private ImageProcessor imageProcessor;
    private NotificationManager notificationManager;
    private int notifySetting = 2; // 0: Silent, 1: Result Only, 2: Detailed, 3: Detailed Auto-Dismiss
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 400;
    private long lastNotificationUpdateTime = 0;

    public interface CancelListener {
        void onCancelRequestedFromNotification();
    }

    private volatile CancelListener cancelListener;

    public class LocalBinder extends Binder {
        public ProcessingService getService() {
            return ProcessingService.this;
        }
    }

    public void setCancelListener(CancelListener listener) {
        this.cancelListener = listener;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        imageProcessor = new ImageProcessor();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannels();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_TASK.equals(intent.getAction())) {
            Log.i(TAG, "Received stop action from notification");
            CancelListener listener = this.cancelListener;
            if (listener != null) {
                listener.onCancelRequestedFromNotification();
            }
            if (imageProcessor != null) {
                imageProcessor.cancelCurrentTask();
            }
            stopForeground(true);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (imageProcessor != null) {
            imageProcessor.shutdown();
        }
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel progressChannel = new NotificationChannel(
                    CHANNEL_ID_PROGRESS,
                    getString(R.string.notification_channel_progress),
                    NotificationManager.IMPORTANCE_LOW);
            progressChannel.setDescription("Quiet ongoing progress while enhancing photos in the background");

            NotificationChannel aliveChannel = new NotificationChannel(
                    CHANNEL_ID_ALIVE,
                    getString(R.string.notification_channel_alive),
                    NotificationManager.IMPORTANCE_LOW);
            aliveChannel.setDescription("Keeps background upscaling active");

            NotificationChannel resultChannel = new NotificationChannel(
                    CHANNEL_ID_RESULT,
                    "Enhancement Complete",
                    NotificationManager.IMPORTANCE_HIGH);
            resultChannel.setDescription("Notifies you with a preview when background upscaling finishes");

            notificationManager.createNotificationChannel(progressChannel);
            notificationManager.createNotificationChannel(aliveChannel);
            notificationManager.createNotificationChannel(resultChannel);
        }
    }

    public void startTask(String command, String workingDir, int notifySetting,
            ImageProcessor.ProcessCallback callback) {
        this.notifySetting = notifySetting;
        this.lastNotificationUpdateTime = 0;
        startForeground(
                NOTIFICATION_ID,
                buildProgressNotification(getString(R.string.app_name), "Preparing photo…", -1)
        );

        imageProcessor.executeCommand(command, workingDir, new ImageProcessor.ProcessCallback() {
            @Override
            public void onProgress(String line) {
                if (callback != null) {
                    callback.onProgress(line);
                }
            }

            @Override
            public void onCompleted(String result, boolean success) {
                boolean removeNotification = (notifySetting == 0 || notifySetting == 3);
                stopForeground(removeNotification);
                if (!removeNotification) {
                    forceUpdateNotification(success ?
                            getString(R.string.done) :
                            getString(R.string.notification_fail));
                }
                if (callback != null) {
                    callback.onCompleted(result, success);
                }
            }

            @Override
            public void onError(String error) {
                boolean removeNotification = (notifySetting == 0 || notifySetting == 3);
                stopForeground(removeNotification);
                if (!removeNotification) {
                    forceUpdateNotification(getString(R.string.notification_fail));
                }
                if (callback != null) {
                    callback.onError(error);
                }
            }
        });
    }

    public void cancelTask() {
        if (imageProcessor != null) {
            imageProcessor.cancelCurrentTask();
        }
    }

    public void updateStructuredProgress(String title, String statusText, int determinatePercent) {
        if (notifySetting == 0) return;
        long now = System.currentTimeMillis();
        if (determinatePercent >= 0 && now - lastNotificationUpdateTime < NOTIFICATION_UPDATE_INTERVAL_MS) {
            return;
        }
        lastNotificationUpdateTime = now;
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return;
        notificationManager.notify(
                NOTIFICATION_ID,
                buildProgressNotification(title, statusText, determinatePercent)
        );
    }

    public void postCompletionNotification(long creationId, String title, String summaryText, File outputFile) {
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            return;
        }
        int notifId = RESULT_NOTIFICATION_BASE_ID + (int) (Math.abs(creationId) % 5000L);

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.putExtra(EXTRA_OPEN_CREATION_ID, creationId);
        openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                notifId,
                openIntent,
                piFlags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_RESULT)
                .setSmallIcon(R.drawable.ic_stat_realscale)
                .setContentTitle(title)
                .setContentText(summaryText)
                .setContentIntent(openPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        if (outputFile != null && outputFile.isFile()) {
            Bitmap preview = decodeNotificationPreview(outputFile, 512);
            if (preview != null) {
                builder.setLargeIcon(preview);
                builder.setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(preview)
                        .bigLargeIcon((Bitmap) null)
                        .setSummaryText(summaryText));
            }
            try {
                PendingIntent sharePi = MediaExportHelper.INSTANCE.buildSharePendingIntent(
                        this,
                        outputFile,
                        notifId + 10000
                );
                builder.addAction(0, "Share", sharePi);
            } catch (Exception e) {
                Log.w(TAG, "Unable to attach Share PendingIntent", e);
            }
        }

        builder.addAction(0, "Open", openPendingIntent);
        notificationManager.notify(notifId, builder.build());
    }

    private void forceUpdateNotification(String text) {
        if (notifySetting == 0) return;
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return;
        lastNotificationUpdateTime = System.currentTimeMillis();
        notificationManager.notify(
                NOTIFICATION_ID,
                buildProgressNotification(getString(R.string.app_name), text, -1)
        );
    }

    private Notification buildProgressNotification(String title, String statusText, int determinatePercent) {
        String channelId = (notifySetting == 2 || notifySetting == 3) ? CHANNEL_ID_PROGRESS : CHANNEL_ID_ALIVE;

        Intent openIntent = new Intent(this, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent openPendingIntent = PendingIntent.getActivity(this, 0, openIntent, piFlags);

        Intent stopIntent = new Intent(this, ProcessingService.class);
        stopIntent.setAction(ACTION_STOP_TASK);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 901, stopIntent, piFlags);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setContentTitle(title)
                .setContentText(statusText)
                .setSmallIcon(R.drawable.ic_stat_realscale)
                .setContentIntent(openPendingIntent)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .addAction(0, "Cancel", stopPendingIntent);

        if (determinatePercent >= 0) {
            int clamped = Math.max(0, Math.min(100, determinatePercent));
            builder.setProgress(100, clamped, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        return builder.build();
    }

    private static Bitmap decodeNotificationPreview(File file, int maxEdge) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            while ((bounds.outWidth / sample) > maxEdge || (bounds.outHeight / sample) > maxEdge) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }
}
