package com.wwh.cpugpu_monitor_android;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.text.BoringLayout;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Arrays;

public class FloatingWidgetService extends Service {

    private WindowManager mWindowManager;
    private View mFloatingWidget;
    private TextView infoTextView;
    private int[] metrics_config = new int[9];
    boolean isFreq = false;
    boolean isUsage = false;
    boolean isTemp = false;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getExtras() != null) {
            // 安全地处理 intent 的额外数据
            Bundle setting = new Bundle();
            setting = intent.getExtras();
            if (setting != null) {
                // 获取配置选项——监控项
                metrics_config = setting.getIntArray("metrics");
                // 获取配置选项——监控指标
                isFreq = setting.getBoolean("isFreq");
                isUsage = setting.getBoolean("isUsage");
                isTemp = setting.getBoolean("isTemp");
            }
        }
        if (mFloatingWidget == null) {
            mFloatingWidget = LayoutInflater.from(this).inflate(R.layout.floating_widget_layout, null);
            // 设置悬浮窗口的参数
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.LEFT;
            mFloatingWidget.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            mWindowManager.updateViewLayout(mFloatingWidget, params);
                            return true;
                        default:
                            return false;
                    }
                }
            });
            // 添加悬浮窗口到WindowManager
            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            mWindowManager.addView(mFloatingWidget, params);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingWidget != null && mWindowManager != null) {
            mWindowManager.removeView(mFloatingWidget);
        }
        unregisterReceiver(updateReceiver);
    }

    public void updateText(String newText) {
        infoTextView = mFloatingWidget.findViewById(R.id.info_text_view);
        if (infoTextView != null) {
            infoTextView.setText(newText);
        }
    }

    @SuppressLint("DefaultLocale")
    public void dealMetrics(metrics[] metrics) {
        StringBuilder text = new StringBuilder("part frequency usage temperature\n");
        for (int i=0;i<metrics.length;i++) {
            if(metrics_config[i]!=1)
            {
                continue;
            }
            metrics metric = metrics[i];
            if (metric != null) {
                String part = metric.getName() == null ? "null" : metric.getName();
                String frequency = metric.getFreq() == -1 ? "null" : String.valueOf(metric.getFreq());
                if(metric.getName().contains("CPU") && metric.getFreq() != -1){
                    frequency = String.format("%.3f", metric.getFreq() / 1000000.0) + "GHz";
                }
                if(metric.getName().contains("GPU") && metric.getFreq() != -1){
                    frequency = String.valueOf(metric.getFreq() / 1000) + (metric.getMemo()==-1?"":("("+String.valueOf(metric.getMemo() / 1000)+")")) + "MHz";
                }
                String usage = metric.getUsage() == -1.0 ? "null" : String.format("%.3f", metric.getUsage());
                String temperature = metric.getTemp() == -100 ? "null" : String.format("%.1f", metric.getTemp());
                text.append(part).append(" ").append(frequency).append(" ").append(usage).append(" ").append(temperature).append("\n");
            }
        }
        updateText(text.toString());
    }

    // 自定义BroadcastReceiver
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // 这里检查动作字符串是否与发送的动作字符串匹配
            if ("com.wwh.UPDATE_ACTION".equals(intent.getAction())) {
                // 调用更新方法
                // 从Intent中取出参数
                Parcelable[] parcelables = intent.getParcelableArrayExtra("data_array");
                if (parcelables != null) {
                    metrics[] metricsDataArray = Arrays.copyOf(parcelables, parcelables.length, metrics[].class);
                    dealMetrics(metricsDataArray);
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        // 注册广播接收器，以监听特定动作的Intent
        IntentFilter filter = new IntentFilter("com.wwh.UPDATE_ACTION");
        registerReceiver(updateReceiver, filter);
    }
}
