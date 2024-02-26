package com.wwh.cpugpu_monitor_android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.ArrayList;
import java.util.List;

public class MonitorService extends Service {

    private int[] metrics = new int[9];
    boolean isMali = false;
    boolean isAdreno = true;
    boolean isFreq = false;
    boolean isUsage = false;
    boolean isTemp = false;
    private volatile boolean isRunning = true;
    private Context context;
    private Thread thd;


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Monitor", "onBind...");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Monitor", "onStartCommand...");
        if (intent != null && intent.getExtras() != null) {
            // 安全地处理 intent 的额外数据
            Bundle setting = new Bundle();
            setting = intent.getExtras();
            if (setting != null) {
                // 获取配置选项——监控项
                metrics = setting.getIntArray("metrics");
                // 获取配置选项——平台项
                isMali = setting.getBoolean("isMali");
                isAdreno = setting.getBoolean("isAdreno");
                // 获取配置选项——监控指标
                isFreq = setting.getBoolean("isFreq");
                isUsage = setting.getBoolean("isUsage");
                isTemp = setting.getBoolean("isTemp");
            }
        } else {
            // 处理传递给服务的 intent 是 null 的情况
            Log.e("Monitor_Err", "onStartCommand Error: Intent is null...");
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Notification notification = createForegroundNotification();
        startForeground(1, notification);
        Log.d("Monitor", "onCreate...");
        context = getApplicationContext();
        // 开启新线程
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doSomething();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    protected Notification createForegroundNotification() {
        //前台通知的id名，任意
        String channelId = "ForegroundService";
        //前台通知的名称，任意
        String channelName = "Service";
        //发送通知的等级，此处为高，根据业务情况而定
        int importance = NotificationManager.IMPORTANCE_HIGH;
        //判断Android版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, channelName, importance);
            channel.setLightColor(Color.BLUE);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        //点击通知时可进入的Activity
        //Intent notificationIntent = new Intent(this,MainActivity.class);
        //PendingIntent pendingIntent = PendingIntent.getActivity(this,0,notificationIntent,0);

        //通知内容
        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("通知标题")
                .setContentText("内容")
                // .setContentIntent(pendingIntent)//点击通知进入Activity
                .setTicker("提示语")
                .build();
    }

    private void doSomething() throws InterruptedException {
        List<String> commands = executeOneMonitor();
        Adblib adb = new Adblib(this);
        // Init执行一次
        // 开启一个新线程执行监控命令
        thd = new Thread(new Runnable() {
            @Override
            public void run() {
                if (adb.openStream()) {
                    // 开启接收线程
                    adb.startDealThread();
                    // 执行监控命令
                    adb.execAdbCommandInit(commands);
                    try {
                        Thread.sleep(1000 * 5);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    // 关闭接收线程并更新窗口
                    if (!adb.getStream().isClosed()) {
                        adb.closeStream();
                    }
                } else {
                    Log.e("Monitor", "Adblib openStream failed!");
                }
            }
        });
        thd.start();
        Log.d("Monitor", "Init Finished...");
        // 等待一段时间1.5s，关闭接收线程
        try {
            Thread.sleep(1000 * 10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // 每隔一段时间执行一次
        while (isRunning) {
            // 开启一个新线程执行监控命令
            thd = new Thread(new Runnable() {
                @Override
                public void run() {
                    if (adb.openStream()) {
                        // 开启接收线程
                        adb.startDealThread();
                        // 执行监控命令
                        adb.execAdbCommand(commands, metrics, isMali, isAdreno, isFreq, isUsage, isTemp);
                        try {
                            Thread.sleep(5 * 1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        // 关闭接收线程并更新窗口
                        if (!adb.getStream().isClosed()) {
                            adb.closeStream();
                        }
                    } else {
                        Log.e("Monitor", "Adblib openStream failed!");
                    }
                }
            });
            thd.start();
            // 等待一段时间1.5s，关闭接收线程
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private List<String> executeOneMonitor() {
        List<String> commands = new ArrayList<>();
        // 获取cpu0-7的信息，遍历metrics数组，根据metrics数组中的值来决定是否获取对应的信息
        for (int i = 0; i < 8; i++) {
            // 获取cpu频率
            commands.add(" echo \"$(cat /sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq)Cpu" + i + "Freq\"");
        }
        // 获取cpu与gpu温度
        commands.add(" dumpsys hardware_properties");
        // 获取cpu使用率
        commands.add(" cat /proc/stat");

        // 获取GPU使用率与频率
        commands.add(" echo \"$(cat /sys/class/kgsl/kgsl-3d0/gpubusy)GpuUsage\"");
        commands.add(" echo \"$(cat /sys/class/kgsl/kgsl-3d0/devfreq/cur_freq)GpuFreq\"");

        // 获取GPU使用率与频率
        commands.add(" echo \"$(cat /sys/module/ged/parameters/gpu_loading)GpuMaliUsage\"");
        commands.add(" cat /proc/gpufreq/gpufreq_var_dump|grep freq");

        // Mali GPU频率
        commands.add("cat /proc/gpufreq/gpufreq_var_dump");
        return commands;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
        isRunning = false; // 设置标识变量为 false，通知线程停止循环
        // 等待线程结束
        try {
            // 需要在子线程中等待，避免阻塞主线程
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (thd.isAlive()) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    stopSelf();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
