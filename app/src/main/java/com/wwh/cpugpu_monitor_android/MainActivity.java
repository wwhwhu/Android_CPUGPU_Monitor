package com.wwh.cpugpu_monitor_android;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.CompoundButton;
import android.widget.Switch;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final int YOUR_PERMISSION_REQUEST_CODE = 1; // 或任意整数
    private Switch switchIsMali;
    private Switch switchIsAdreno;
    private int[] metrics = new int[9];
    public static Intent intent;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == YOUR_PERMISSION_REQUEST_CODE) {
            // 检查是否获得了权限
            if (Settings.canDrawOverlays(this)) {
                // 你有权限继续你的操作
            } else {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("需要悬浮窗权限")
                        .setMessage("此功能需要悬浮窗权限，请在设置中授权！")
                        .setPositiveButton("去设置", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // 引导用户到设置中去进行授权
                                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, YOUR_PERMISSION_REQUEST_CODE);
                            }
                        })
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // 用户取消操作
                                dialog.dismiss();
                            }
                        });
                // 创建并显示AlertDialog
                builder.create().show();
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        switchIsMali = findViewById(R.id.switch_isMali);
        switchIsAdreno = findViewById(R.id.switch_isAdreno);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, YOUR_PERMISSION_REQUEST_CODE);
        }

        // 设置isMali与isAdreno状态互斥
        switchIsMali.setOnClickListener(v -> {
            if (switchIsMali.isChecked()) {
                switchIsAdreno.setChecked(false);
            }
            else{
                switchIsAdreno.setChecked(true);
            }
        });

        switchIsAdreno.setOnClickListener(v -> {
            if (switchIsAdreno.isChecked()) {
                switchIsMali.setChecked(false);
            }
            else{
                switchIsMali.setChecked(true);
            }
        });


        intent = new Intent(MainActivity.this, MonitorService.class);
        // 获取需要监控的指标

        // 点击按钮，开启一个前台Service，用于监控CPU和GPU
        findViewById(R.id.button_start_monitoring).setOnClickListener(v -> {
            // 如果显示的是关闭监控，则点击后开启监控
            if (((android.widget.Button) v).getText().equals("正在监控...点击关闭")) {
                ((android.widget.Button) v).setText("开始监控");
                findViewById(R.id.switch_cpu0).setClickable(true);
                findViewById(R.id.switch_cpu1).setClickable(true);
                findViewById(R.id.switch_cpu2).setClickable(true);
                findViewById(R.id.switch_cpu3).setClickable(true);
                findViewById(R.id.switch_cpu4).setClickable(true);
                findViewById(R.id.switch_cpu5).setClickable(true);
                findViewById(R.id.switch_cpu6).setClickable(true);
                findViewById(R.id.switch_cpu7).setClickable(true);
                findViewById(R.id.switch_gpu).setClickable(true);
                findViewById(R.id.switch_isMali).setClickable(true);
                findViewById(R.id.switch_isAdreno).setClickable(true);
                stopService(new Intent(MainActivity.this, MonitorService.class));
                stopService(new Intent(MainActivity.this, FloatingWidgetService.class));
                return;
            }
            if (((android.widget.Button) v).getText().equals("开始监控")) {
                // 创建一个 SpannableString 对象，设置标题的颜色
                Intent UI = new Intent(this, FloatingWidgetService.class);
                String titleText = "重要提示";
                SpannableString spannableString = new SpannableString(titleText);
                ForegroundColorSpan colorSpan = new ForegroundColorSpan(Color.RED);
                spannableString.setSpan(colorSpan, 0, titleText.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);

                // 弹出提示框
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                builder.setTitle(spannableString);
                builder.setMessage("1.请使用USB线连接电脑与本设备\n2.使用”win+r+cmd”打开命令行窗口\n3.运行”adb tcpip 5555”命令");
                builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 点击确定后执行的操作
                        Switch sw0 = findViewById(R.id.switch_cpu0);
                        metrics[0] = sw0.isChecked() ? 1 : 0;
                        Switch sw1 = findViewById(R.id.switch_cpu1);
                        metrics[1] = sw1.isChecked() ? 1 : 0;
                        Switch sw2 = findViewById(R.id.switch_cpu2);
                        metrics[2] = sw2.isChecked() ? 1 : 0;
                        Switch sw3 = findViewById(R.id.switch_cpu3);
                        metrics[3] = sw3.isChecked() ? 1 : 0;
                        Switch sw4 = findViewById(R.id.switch_cpu4);
                        metrics[4] = sw4.isChecked() ? 1 : 0;
                        Switch sw5 = findViewById(R.id.switch_cpu5);
                        metrics[5] = sw5.isChecked() ? 1 : 0;
                        Switch sw6 = findViewById(R.id.switch_cpu6);
                        metrics[6] = sw6.isChecked() ? 1 : 0;
                        Switch sw7 = findViewById(R.id.switch_cpu7);
                        metrics[7] = sw7.isChecked() ? 1 : 0;
                        Switch sw8 = findViewById(R.id.switch_gpu);
                        metrics[8] = sw8.isChecked() ? 1 : 0;

                        sw0.setClickable(false);
                        sw1.setClickable(false);
                        sw2.setClickable(false);
                        sw3.setClickable(false);
                        sw4.setClickable(false);
                        sw5.setClickable(false);
                        sw6.setClickable(false);
                        sw7.setClickable(false);
                        sw8.setClickable(false);
                        Switch sw_frep = findViewById(R.id.switch_freq);
                        Switch sw_usage = findViewById(R.id.switch_usage);
                        Switch sw_temp = findViewById(R.id.switch_temp);
                        findViewById(R.id.switch_isMali).setClickable(false);
                        findViewById(R.id.switch_isAdreno).setClickable(false);
                        sw_frep.setClickable(false);
                        sw_usage.setClickable(false);
                        sw_temp.setClickable(false);
                        Bundle bundle = new Bundle();
                        bundle.putIntArray("metrics", metrics);
                        bundle.putBoolean("isMali", switchIsMali.isChecked() ? true : false);
                        bundle.putBoolean("isAdreno", switchIsAdreno.isChecked() ? true : false);
                        bundle.putBoolean("isFreq", sw_frep.isChecked() ? true : false);
                        bundle.putBoolean("isUsage", sw_usage.isChecked() ? true : false);
                        bundle.putBoolean("isTemp", sw_temp.isChecked() ? true : false);
                        intent.putExtras(bundle);
                        UI.putExtras(bundle);
                        ((android.widget.Button) v).setText("正在监控...点击关闭");
                        // 启动 FloatingWidgetService
                        startService(UI);
                        // 启动 MonitorService
                        startForegroundService(intent);
                    }
                });
                builder.create().show();
            }
        });
    }
}