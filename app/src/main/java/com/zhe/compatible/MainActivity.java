package com.zhe.compatible;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.system.Os;
import android.system.OsConstants;
import java.io.FileDescriptor;
import java.io.IOException;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Color;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private TextView reportView;
    private String lastReport = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(28, 24, 28, 24);
        page.setBackgroundColor(Color.rgb(18, 24, 32));

        TextView title = new TextView(this);
        title.setText("ZHE AUDIO DIAGNOSTIC");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        page.addView(title, lp());

        TextView intro = new TextView(this);
        intro.setText("أداة فحص آمنة لمعرفة واجهات الصوت ومشغل التلفزيون التي يتيحها نظام الرسيفر. لا تستخدم الميكروفون ولا تغيّر إعدادات النظام.");
        intro.setTextColor(Color.LTGRAY);
        intro.setTextSize(17);
        intro.setPadding(0, 16, 0, 18);
        page.addView(intro, lp());

        Button scan = new Button(this);
        scan.setText("بدء الفحص وجمع التقرير");
        scan.setTextSize(18);
        page.addView(scan, lp());
        scan.setOnClickListener(v -> runScan());

        Button access = new Button(this);
        access.setText("اختبار فتح أجهزة DVB والصوت (آمن)");
        access.setTextSize(17);
        page.addView(access, lp());
        access.setOnClickListener(v -> runAccessProbe());

        Button copy = new Button(this);
        copy.setText("نسخ التقرير");
        page.addView(copy, lp());
        copy.setOnClickListener(v -> {
            if (lastReport.length() == 0) runScan();
            ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("ZHE diagnostic", lastReport));
            new AlertDialog.Builder(this).setMessage("تم نسخ التقرير. أرسله هنا في المحادثة.")
                .setPositiveButton("موافق", null).show();
        });

        Button share = new Button(this);
        share.setText("مشاركة التقرير");
        page.addView(share, lp());
        share.setOnClickListener(v -> {
            if (lastReport.length() == 0) runScan();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "ZHE AUDIO DIAGNOSTIC");
            i.putExtra(Intent.EXTRA_TEXT, lastReport);
            startActivity(Intent.createChooser(i, "إرسال تقرير الفحص"));
        });

        reportView = new TextView(this);
        reportView.setText("اضغط بدء الفحص. سيظهر التقرير هنا.");
        reportView.setTextColor(Color.rgb(220, 230, 240));
        reportView.setTextSize(14);
        reportView.setTextIsSelectable(true);
        reportView.setPadding(0, 22, 0, 20);
        page.addView(reportView, lp());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        setContentView(scroll);
    }

    private void runScan() {
        StringBuilder s = new StringBuilder();
        s.append("ZHE AUDIO DIAGNOSTIC REPORT\n");
        s.append("وقت الفحص: ").append(new java.util.Date()).append("\n\n");
        s.append("[معلومات النظام]\n");
        s.append("الشركة: ").append(Build.MANUFACTURER).append("\n");
        s.append("الجهاز: ").append(Build.DEVICE).append("\n");
        s.append("الطراز: ").append(Build.MODEL).append("\n");
        s.append("المنتج: ").append(Build.PRODUCT).append("\n");
        s.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n");

        s.append("[خدمة الصوت AudioManager]\n");
        try {
            AudioManager am = (AudioManager)getSystemService(AUDIO_SERVICE);
            s.append("وضع الصوت: ").append(am.getMode()).append("\n");
            s.append("تشغيل الموسيقى نشط: ").append(am.isMusicActive()).append("\n");
            s.append("تردد الإخراج الافتراضي: ").append(am.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)).append("\n");
            s.append("حجم مخزن الإخراج: ").append(am.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)).append("\n");
            s.append("أجهزة الصوت المعروفة للنظام:\n");
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_ALL);
            if (devices.length == 0) s.append(" - لا توجد أجهزة معلنة عبر AudioManager\n");
            for (AudioDeviceInfo d : devices) {
                s.append(" - id=").append(d.getId())
                 .append(" النوع=").append(deviceType(d.getType()))
                 .append(" المنتج=").append(d.getProductName())
                 .append(" مصدر=").append(d.isSource())
                 .append(" وجهة=").append(d.isSink()).append("\n");
            }
        } catch (Throwable t) { s.append("تعذر قراءة AudioManager: ").append(t).append("\n"); }
        s.append("\n");

        s.append("[مسارات أجهزة الصوت وDVB]\n");
        String[] paths = {"/dev/snd", "/proc/asound", "/sys/class/sound", "/dev/dvb", "/dev/dvb0.demux6", "/dev/dvb0.dvr6", "/dev/dvb/adapter0", "/dev/dvb/adapter0/demux0", "/dev/dvb/adapter0/dvr0", "/dev/aml", "/sys/class/aml"};
        for (String p : paths) appendFileInfo(s, p);
        s.append("\n");

        s.append("[مجلدات /dev ذات الصلة]\n");
        listDir(s, "/dev", "snd|dvb|audio|aml|tv|demux|dvr");
        s.append("\n");

        s.append("[تطبيقات التلفزيون أو IPTV المثبتة]\n");
        try {
            List<android.content.pm.ApplicationInfo> apps = getPackageManager().getInstalledApplications(0);
            int found = 0;
            for (android.content.pm.ApplicationInfo ai : apps) {
                String p = ai.packageName.toLowerCase();
                if (p.contains("tv") || p.contains("dvb") || p.contains("iptv") || p.contains("tiger") || p.contains("zhe") || p.contains("amlogic") || p.contains("media")) {
                    s.append(" - ").append(ai.packageName).append("\n"); found++;
                }
            }
            if (found == 0) s.append(" - لم يظهر اسم مطابق في قائمة التطبيقات المرئية\n");
        } catch (Throwable t) { s.append("تعذر تعداد التطبيقات: ").append(t).append("\n"); }
        s.append("\n[حدود الفحص]\n");
        s.append("هذا التقرير يقرأ المعلومات التي يسمح بها Android للتطبيق العادي فقط. وجود مسار لا يعني إمكانية فتحه أو التقاط صوت القناة. لا يحاول التطبيق فتح أجهزة النظام أو تجاوز صلاحياتها.\n");

        lastReport = s.toString();
        reportView.setText(lastReport);
    }

    // Opens each device read-only and non-blocking, then closes it immediately.
    // It does not read data, issue ioctls, configure DVB filters, or write to devices.
    private void runAccessProbe() {
        StringBuilder s = new StringBuilder();
        s.append("\n\n[اختبار فتح أجهزة النظام - قراءة فقط]\n");
        s.append("UID التطبيق: ").append(android.os.Process.myUid()).append("\n");
        s.append("PID التطبيق: ").append(android.os.Process.myPid()).append("\n");
        String[] paths = {
            "/dev/dvb0.demux0", "/dev/dvb0.demux1", "/dev/dvb0.demux2",
            "/dev/dvb0.dvr0", "/dev/dvb0.dvr1", "/dev/dvb0.dvr2",
            "/dev/snd/pcmC0D3c", "/dev/snd/pcmC0D7c", "/dev/snd/pcmC0D2c",
            "/dev/amaudio", "/dev/amaudio_ctl", "/dev/audio_data_debug"
        };
        for (String path : paths) {
            File f = new File(path);
            s.append(path).append(" | موجود=").append(f.exists());
            if (!f.exists()) {
                s.append(" | النتيجة=غير موجود\n");
                continue;
            }
            FileDescriptor fd = null;
            try {
                fd = Os.open(path, OsConstants.O_RDONLY | OsConstants.O_NONBLOCK, 0);
                s.append(" | النتيجة=نجح الفتح للقراءة فقط");
            } catch (Throwable t) {
                s.append(" | النتيجة=فشل الفتح");
                s.append(" | السبب=").append(t.getClass().getSimpleName());
                if (t.getMessage() != null) s.append(": ").append(t.getMessage());
            } finally {
                if (fd != null) {
                    try { Os.close(fd); s.append(" | أُغلق فوراً"); }
                    catch (Throwable closeError) { s.append(" | تعذر الإغلاق: ").append(closeError.getClass().getSimpleName()); }
                }
            }
            s.append("\n");
        }
        s.append("ملاحظة: نجاح الفتح لا يعني أن الجهاز يحمل صوت القناة. لم تتم قراءة أي بيانات ولم يتم ضبط DVB أو إرسال أوامر تحكم.\n");
        lastReport += s.toString();
        reportView.setText(lastReport);
    }

    private void appendFileInfo(StringBuilder s, String path) {
        File f = new File(path);
        s.append(path).append(" | موجود=").append(f.exists())
         .append(" | قراءة=").append(f.canRead())
         .append(" | كتابة=").append(f.canWrite());
        if (f.isDirectory()) {
            String[] names = f.list();
            s.append(" | مجلد");
            if (names != null) {
                s.append(" | المحتويات: ");
                for (int i = 0; i < Math.min(names.length, 25); i++) {
                    if (i > 0) s.append(", ");
                    s.append(names[i]);
                }
                if (names.length > 25) s.append("...");
            }
        }
        s.append("\n");
    }

    private void listDir(StringBuilder s, String path, String regex) {
        File d = new File(path);
        String[] names = d.list();
        if (names == null) { s.append("تعذر عرض ").append(path).append("\n"); return; }
        int n = 0;
        for (String name : names) {
            if (name.matches("(?i).*(" + regex + ").*")) {
                appendFileInfo(s, path + "/" + name);
                n++;
            }
        }
        if (n == 0) s.append("لا توجد أسماء مطابقة أو لا يسمح النظام بعرضها\n");
    }

    private String deviceType(int t) {
        switch (t) {
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "سماعة داخلية";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "سماعة الجهاز";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "سماعات سلكية";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "سماعة مع ميكروفون";
            case AudioDeviceInfo.TYPE_HDMI: return "HDMI";
            case AudioDeviceInfo.TYPE_HDMI_ARC: return "HDMI ARC";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headset";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth A2DP";
            case AudioDeviceInfo.TYPE_LINE_ANALOG: return "Line analog";
            case AudioDeviceInfo.TYPE_LINE_DIGITAL: return "Line digital";
            default: return "type-" + t;
        }
    }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
