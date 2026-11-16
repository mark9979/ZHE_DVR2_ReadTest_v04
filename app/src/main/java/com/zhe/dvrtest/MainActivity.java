package com.zhe.dvrtest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;
import android.system.OsConstants;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.FileDescriptor;
import java.util.Locale;

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
        title.setText("ZHE AUDIO PRO — اختبار DVR2 v0.4");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title);

        TextView warning = new TextView(this);
        warning.setText("اختبار قراءة فقط بحد أقصى 1880 بايت ولمدة ثانية واحدة. قد يحدث تقطع لحظي إذا كان التدفق مشتركاً. لا يغيّر القناة ولا يرسل أوامر DVB.");
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
        status.setText("جاهز. شغّل قناة فضائية أولاً، ثم اضغط زر الاختبار.");
        status.setPadding(0, 20, 0, 0);
        page.addView(status);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);

        runButton.setOnClickListener(v -> startTest());
    }

    private void startTest() {
        runButton.setEnabled(false);
        status.setText("جارٍ فتح /dev/dvb0.dvr2 بوضع قراءة غير حاجب...\n");
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
        FileDescriptor fd = null;
        long start = System.nanoTime();
        int total = 0;
        int reads = 0;
        int emptyReads = 0;
        String error = null;
        byte[] buffer = new byte[1880]; // 10 MPEG-TS packets
        try {
            fd = Os.open(path, OsConstants.O_RDONLY | OsConstants.O_NONBLOCK, 0);
            long deadline = System.nanoTime() + 1_000_000_000L;
            while (total < buffer.length && System.nanoTime() < deadline) {
                int n = Os.read(fd, buffer, total, buffer.length - total);
                if (n > 0) {
                    total += n;
                    reads++;
                } else {
                    emptyReads++;
                    try { Thread.sleep(20); } catch (InterruptedException ignored) { break; }
                }
            }
        } catch (Exception e) {
            error = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            if (fd != null) {
                try { Os.close(fd); } catch (Exception ignored) {}
            }
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        StringBuilder out = new StringBuilder();
        out.append("نتيجة الاختبار\n");
        out.append("المسار: ").append(path).append('\n');
        out.append("المدة: ").append(elapsedMs).append(" ms\n");
        out.append("البيانات المقروءة: ").append(total).append(" / 1880 بايت\n");
        out.append("عمليات القراءة الناجحة: ").append(reads).append('\n');
        out.append("القراءات الفارغة: ").append(emptyReads).append('\n');
        out.append("تم إغلاق الملف فور انتهاء الاختبار.\n");
        if (error != null) {
            out.append("الخطأ: ").append(error).append('\n');
        } else if (total > 0) {
            out.append("التفسير: الجهاز أعاد بيانات. هذا لا يثبت أنها صوت القناة أو أنها قابلة للفك.\n");
            int sync = 0;
            for (int i = 0; i < total; i += 188) {
                if ((buffer[i] & 0xFF) == 0x47) sync++;
            }
            out.append("علامات تزامن MPEG-TS عند فواصل 188 بايت: ").append(sync).append('\n');
        } else {
            out.append("التفسير: لم تصل بيانات خلال المهلة. قد يكون المسار غير نشط أو محجوزاً أو لا يمرر البيانات لهذا التطبيق.\n");
        }
        out.append("\nتنبيه: الاختبار قراءة محدودة فقط؛ لا يحدد PID الصوت ولا يلتقط الصوت ولا ينفذ المزامنة.");
        return out.toString();
    }
}
