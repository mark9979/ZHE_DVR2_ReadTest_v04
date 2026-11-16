package com.zhe.compatible;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FloatingService extends Service {
    public static final String ACTION_SHOW = "com.zhe.compatible.SHOW";
    public static final String ACTION_HIDE = "com.zhe.compatible.HIDE";
    private static final String CHANNEL_ID = "zhe_floating_service";
    private static final int NOTIFICATION_ID = 7319;
    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams overlayParams;

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_HIDE.equals(action)) {
            removeFloatingView();
        } else {
            showFloatingView();
        }
        return START_STICKY;
    }

    private void showFloatingView() {
        if (floatingView != null) return;
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(238, 24, 31, 42));
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), Color.rgb(70, 160, 220));
        panel.setBackground(background);
        TextView heading = new TextView(this);
        heading.setText("ZHE AUDIO PRO");
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(16);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading, wrap());

        TextView info = new TextView(this);
        info.setText("النافذة العائمة نشطة");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(12);
        info.setGravity(Gravity.CENTER);
        panel.addView(info, wrap());

        Button hide = new Button(this);
        hide.setText("إخفاء النافذة");
        hide.setFocusable(true);
        panel.addView(hide, wrap());
        hide.setOnClickListener(v -> removeFloatingView());

        Button close = new Button(this);
        close.setText("إيقاف الخدمة");
        close.setFocusable(true);
        panel.addView(close, wrap());
        close.setOnClickListener(v -> stopSelf());

        int type = Build.VERSION.SDK_INT >= 26
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
        overlayParams = new WindowManager.LayoutParams(
            dp(220), WindowManager.LayoutParams.WRAP_CONTENT, type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        overlayParams.gravity = Gravity.TOP | Gravity.RIGHT;
        overlayParams.x = dp(18);
        overlayParams.y = dp(80);
        panel.setFocusableInTouchMode(true);
        panel.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float touchX, touchY;
            public boolean onTouch(View view, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    startX = overlayParams.x;
                    startY = overlayParams.y;
                    touchX = event.getRawX();
                    touchY = event.getRawY();
                    return false;
                }
                if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    overlayParams.x = startX - (int)(event.getRawX() - touchX);
                    overlayParams.y = startY + (int)(event.getRawY() - touchY);
                    try { windowManager.updateViewLayout(floatingView, overlayParams); }
                    catch (Exception ignored) {}
                    return true;
                }
                return false;
            }
        });
        floatingView = panel;
        try {
            windowManager.addView(floatingView, overlayParams);
            floatingView.requestFocus();
        } catch (Exception e) {
            floatingView = null;
            stopSelf();
        }
    }

    private void removeFloatingView() {
        if (floatingView != null) {
            try { windowManager.removeView(floatingView); }
            catch (Exception ignored) {}
            floatingView = null;
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, open, pendingFlags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);
        return builder.setContentTitle("ZHE AUDIO PRO")
            .setContentText("الخدمة العائمة تعمل — اضغط للعودة إلى التطبيق")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pending)
            .setOngoing(true)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "ZHE Floating Service", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
    }

    private int dp(int value) {
        return (int)(value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    @Override public void onDestroy() {
        removeFloatingView();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
