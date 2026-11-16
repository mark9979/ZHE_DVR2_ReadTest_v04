package com.zhe.dvrtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView status;
    private Button runButton;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(36, 28, 36, 28);
        page.setBackgroundColor(0xFF101820);

        TextView title = new TextView(this);
        title.setText("ZHE AUDIO PRO — اختبار DVR2 v0.4.1");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title);

        TextView warning = new TextView(this);
        warning.setText("قراءة فقط لمدة أقصاها ثانية واحدة وبحد أقصى 1880 بايت. يعيد الاختبار المحاولة عند EAGAIN. قد يؤثر على التدفق المشترك.");
        warning.setTextColor(0xFFFFCC80);
        warning.setTextSize(18);
        warning.setPadding(0, 18, 0, 18);
        page.addView(warning);

        runButton = new Button(this);
        runButton.setText("ابدأ الاختبار القصير");
        runButton.setTextSize(20);
        page.addView(runButton);

        status = new TextView(this);
        status.setTextColor(0xFFE6EDF3);
        status.setTextSize(17);
        status.setPadding(0, 20, 0, 0);
        status.setText("جاهز. شغّل قناة فضائية ثم اضغط زر الاختبار.");
        page.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
        runButton.setOnClickListener(v -> startTest());
    }

    private void startTest() {
        runButton.setEnabled(false);
        status.setText("جارٍ الاختبار لمدة أقصاها ثانية...");
        new Thread(() -> {
            String result = performReadTest();
            handler.post(() -> {
                status.setText(result);
                runButton.setEnabled(true);
            });
        }, "DVR2-read-test").start();
    }

    private String performReadTest() {
        final String path = "/dev/dvb0.dvr2";
        android.os.ParcelFileDescriptor pfd = null;
        byte[] buffer = new byte[1880];
        int total = 0, reads = 0, eagainCount = 0;
        String error = null;
        long start = System.nanoTime();
        long deadline = start + 1_000_000_000L;

        try {
            java.io.FileDescriptor fd = Os.open(path, OsConstants.O_RDONLY | OsConstants.O_NONBLOCK, 0);
            try {
                while (total < buffer.length && System.nanoTime() < deadline) {
                    try {
                        int n = Os.read(fd, buffer, total, buffer.length - total);
                        if (n > 0) {
                            total += n;
                            reads++;
                        } else {
                            break; // EOF
                        }
                    } catch (ErrnoException e) {
                        if (e.errno == OsConstants.EAGAIN || e.errno == OsConstants.EWOULDBLOCK) {
                            eagainCount++;
                            try { Thread.sleep(20); }
                            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                        } else {
                            throw e;
                        }
                    }
                }
            } finally {
                Os.close(fd);
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        StringBuilder out = new StringBuilder();
        out.append("نتيجة الاختبار v0.4.1\n");
        out.append("المسار: ").append(path).append('\n');
        out.append("المدة: ").append(elapsedMs).append(" ms\n");
        out.append("البيانات المقروءة: ").append(total).append(" / 1880 بايت\n");
        out.append("عمليات القراءة الناجحة: ").append(reads).append('\n');
        out.append("محاولات EAGAIN: ").append(eagainCount).append('\n');
        out.append("تم إغلاق المسار بعد الاختبار.\n");
        if (error != null) {
            out.append("الخطأ: ").append(error).append('\n');
        } else if (total > 0) {
            int sync = 0;
            for (int i = 0; i < total; i += 188) if ((buffer[i] & 0xFF) == 0x47) sync++;
            out.append("علامات تزامن MPEG-TS عند فواصل 188 بايت: ").append(sync).append('\n');
            out.append("التفسير: وصلت بيانات إلى المسار، لكن ذلك لا يثبت أنها صوت القناة.\n");
        } else {
            out.append("التفسير: لم تصل بيانات خلال ثانية الاختبار.\n");
        }
        out.append("تنبيه: لا يحدد الاختبار PID الصوت ولا يفك الصوت ولا ينفذ المزامنة.");
        return out.toString();
    }
}
