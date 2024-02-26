package com.wwh.cpugpu_monitor_android;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.cgutman.adblib.AdbBase64;
import com.cgutman.adblib.AdbConnection;
import com.cgutman.adblib.AdbCrypto;
import com.cgutman.adblib.AdbStream;

import org.apache.commons.codec.binary.Base64;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Adblib {
    private final Object fileDir;
    private final Context context;
    private AdbStream stream;
    private metrics[] metrics = new metrics[10];
    // 保存之前计算CPU利用率相关的信息
    private static int[] cpuLastIdle = new int[9];
    private static int[] cpuLast = new int[9];

    // 构造函数
    public Adblib(Context context) {
        this.fileDir = context.getFilesDir();
        this.context = context;
    }

    private AdbConnection adbConnection;

    public boolean openStream() {
        try {
            InetAddress IPAddr = InetAddress.getByName("127.0.0.1");
            Socket sock = new Socket(IPAddr, 5555);
            AdbCrypto crypto = setupCrypto(fileDir + "/pub.key", fileDir + "/priv.key");
            adbConnection = AdbConnection.create(sock, crypto);
            Log.d("Monitor", "socket connected success!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Monitor", "socket connected error!");
            return false;
        }
        try {
            adbConnection.connect();
            stream = adbConnection.open("shell:");
            Log.d("Monitor", "adb connected success!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("Monitor", "adb connected error!");
            return false;
        }
        metrics = new metrics[10];
        for(int i = 0; i < 10; i++){
            metrics[i] = new metrics();
        }
        metrics[0].setName("CPU0");
        metrics[1].setName("CPU1");
        metrics[2].setName("CPU2");
        metrics[3].setName("CPU3");
        metrics[4].setName("CPU4");
        metrics[5].setName("CPU5");
        metrics[6].setName("CPU6");
        metrics[7].setName("CPU7");
        metrics[8].setName("GPU");
        metrics[9].setName("CPU");
        return true;
    }

    private static AdbCrypto setupCrypto(String pubKeyFile, String privKeyFile)
            throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        File pub = new File(pubKeyFile);
        File priv = new File(privKeyFile);
        AdbCrypto c = null;
        // Try to load a key pair from the files
        if (pub.exists() && priv.exists()) {
            try {
                c = AdbCrypto.loadAdbKeyPair(Adblib.getBase64Impl(), priv, pub);
            } catch (IOException e) {
                // Failed to read from file
                c = null;
            } catch (InvalidKeySpecException e) {
                // Key spec was invalid
                c = null;
            } catch (NoSuchAlgorithmException e) {
                // RSA algorithm was unsupported with the crypo packages available
                c = null;
            }
        }

        if (c == null) {
            // We couldn't load a key, so let's generate a new one
            c = AdbCrypto.generateAdbKeyPair(Adblib.getBase64Impl());

            // Save it
            c.saveAdbKeyPair(priv, pub);
            System.out.println("Generated new keypair");
        } else {
            System.out.println("Loaded existing keypair");
        }
        return c;
    }

    public static AdbBase64 getBase64Impl() {
        return new AdbBase64() {
            @Override
            public String encodeToString(byte[] arg0) {
                return Base64.encodeBase64String(arg0);
            }
        };
    }

    public void execAdbCommand(List<String> commands, int[] metrics, boolean isMali, boolean isAdreno, boolean isFreq, boolean isUsage, boolean isTemp) {
        List<String> newCommands = new ArrayList<>();
        Log.d("Monitor_Res", "command_size: " + commands.size());
        if(commands.size() < 14){
            for(int i = 0; i < commands.size(); i++){
                Log.d("Monitor_Res", "command: " + commands.get(i));
            }
        }
        try {
            boolean isCpu = false;
            // 首先是CPU频率
            for (int i = 0; i < 8; i++) {
                if (metrics[i] > 0) {
                    isCpu = true;
                    if (isFreq)
                        newCommands.add(commands.get(i));
                }
            }
            if (isCpu && isUsage) {
                newCommands.add(commands.get(9));
            }
            // 然后是GPU
            if (metrics[8] > 0) {
                if (isMali) {
                    if (isFreq)
                        newCommands.add(commands.get(12));
                    if (isUsage)
                        newCommands.add(commands.get(13));
                }
                if (isAdreno) {
                    if (isFreq)
                        newCommands.add(commands.get(10));
                    if (isUsage)
                        newCommands.add(commands.get(11));
                }
            }
            // 温度
            if (isTemp && (isCpu || metrics[8] > 0)) {
                newCommands.add(commands.get(8));
            }

            for (String command : newCommands) {
                String cmd = command + "\n";
                Log.d("Raw Command:", "Command: " + cmd);
                stream.write(cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void execAdbCommandInit(List<String> commands) {
        try {
            for (String command : commands) {
                String cmd = command + "\n";
                stream.write(cmd);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startDealThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stream.isClosed()) {
                    String str = null;
                    try {
                        byte[] s = stream.read();
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                            str = new String(s, StandardCharsets.US_ASCII);
                        }
                        Log.d("Raw Results: ", str);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    if (str != null)
                        parseLogResults(str);
                }
            }
        }).start();
    }

    // 解析结果，包括频率，温度，使用率
    public void parseLogResults(String string) {
        // 匹配CPU各个频率
        for (int i = 0; i < 8; i++) {
            String patternStr = "(\\d+)Cpu" + i + "Freq";
            Pattern pattern = Pattern.compile(patternStr);
            Matcher matcher = pattern.matcher(string);
            if (matcher.find()) {
                String freq = matcher.group(1);
                if(metrics[i]!=null) {
                    metrics[i].setFreq(Integer.parseInt(freq));
                }
                Log.d("Monitor_Res: ", "CPU" + i + "Freq: " + freq);
            }
        }
        // 匹配CPU各个使用率
        for (int i = 0; i < 8; i++) {
            String patternStr5 = "cpu" + i + "\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)";
            Pattern pattern5 = Pattern.compile(patternStr5);
            Matcher matcher5 = pattern5.matcher(string);
            if (matcher5.find()) {
                int cpuUser = Integer.parseInt(matcher5.group(1));
                int cpuNice = Integer.parseInt(matcher5.group(2));
                int cpuSystem = Integer.parseInt(matcher5.group(3));
                int cpuIdle = Integer.parseInt(matcher5.group(4));
                int cpuIowait = Integer.parseInt(matcher5.group(5));
                int cpuIrq = Integer.parseInt(matcher5.group(6));
                int cpuSoftirq = Integer.parseInt(matcher5.group(7));
                int cpuSteal = Integer.parseInt(matcher5.group(8));
                int cpuGuest = Integer.parseInt(matcher5.group(9));
                int cpuGnice = Integer.parseInt(matcher5.group(10));
                int now = cpuUser + cpuNice + cpuSystem + cpuIdle + cpuIowait + cpuIrq + cpuSoftirq + cpuSteal + cpuGuest + cpuGnice;
                int interval = now - cpuLast[i];
                double cpuUsage = 1 - (cpuIdle - cpuLastIdle[i]) / (interval + 0.0);
                cpuLast[i] = now;
                cpuLastIdle[i] = cpuIdle;
                metrics[i].setUsage(cpuUsage);
                Log.d("Monitor_Res: ", "CPU" + i + "Usage: " + cpuUsage);
            }
        }

        // 匹配CPU各个温度
        String patternStr = "CPU temperatures:\\s+[(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+)]";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(string);
        if (matcher.find()) {
            String temp = matcher.group(1);
            for (int i = 0; i < 8; i++) {
                metrics[i].setTemp(Double.parseDouble(matcher.group(i + 1)));
                Log.d("Monitor_Res: ", "CPU" + i + "Temp: " + matcher.group(i + 1));
            }
        }

        // 匹配高通GPU使用率
        String patternStrGPU = "\\s*(\\d+)\\s+(\\d+)GpuUsage";
        Pattern patternGPU = Pattern.compile(patternStrGPU);
        Matcher matcherGPU = patternGPU.matcher(string);
        if (matcherGPU.find()) {
            double a = Double.parseDouble(matcherGPU.group(1));
            double b = Double.parseDouble(matcherGPU.group(2));
            if (b != 0 && a != 0 && a < b) {
                metrics[8].setUsage(Double.parseDouble(new DecimalFormat("#.000").format(a / b)));
                Log.d("Monitor_Res: ", "gpuUsage: " + new DecimalFormat("#.000").format(a / b));
            } else if (a == 0 && b == 0) {
                metrics[8].setUsage(0.000);
                Log.d("Monitor_Res: ", "gpuUsage: 0.000");
            }
        }

        // 匹配高通GPU频率
        String patternClkStr = "(\\d+)GpuFreq";
        Pattern patternClk = Pattern.compile(patternClkStr);
        Matcher matcherClk = patternClk.matcher(string);
        if (matcherClk.find()) {
            String clk = matcherClk.group(1);
            metrics[8].setFreq(Integer.parseInt(clk));
            Log.d("Monitor_Res: ", "gpuFreq: " + clk);
        }

        // 匹配MaliGPU使用率
        String MaliPatternUStr = "(\\d+)GpuMaliUsage";
        Pattern MaliPatternU = Pattern.compile(MaliPatternUStr);
        Matcher MaliMatcherU = MaliPatternU.matcher(string);
        if (MaliMatcherU.find()) {
            String us = MaliMatcherU.group(1);
            metrics[8].setUsage(Integer.parseInt(us)/100.0);
            Log.d("Monitor_Res: ", "gpuUsage: " + Integer.parseInt(us)/100.0);
        }

        // 匹配MaliGPU频率
        String MaliPatternStr = "\\(real\\)\\s+freq:\\s+(\\d+),\\s+freq:\\s+(\\d+)";
        Pattern MaliPattern = Pattern.compile(MaliPatternStr);
        Matcher MaliMatcher = MaliPattern.matcher(string);
        if (MaliMatcher.find()) {
            String clk = MaliMatcher.group(1);
            metrics[8].setFreq(Integer.parseInt(clk));
            String clk2 = MaliMatcher.group(2);
            metrics[8].setMemo(Integer.parseInt(clk2));
            Log.d("Monitor_Res: ", "gpuFreq: " + clk);
        }

        // 匹配GPU温度
        String patternGpuTempStr = "GPU temperatures: [(-?\\d+\\.\\d+),\\s+(-?\\d+\\.\\d+)]";
        Pattern patternGpuTemp = Pattern.compile(patternGpuTempStr);
        Matcher matcherGpuTemp = patternGpuTemp.matcher(string);
        if (matcherGpuTemp.find()) {
            String temp0 = matcherGpuTemp.group(1);
            String temp1 = matcherGpuTemp.group(2);
            metrics[8].setTemp((Double.parseDouble(temp0) + Double.parseDouble(temp1)) / 2);
            Log.d("Monitor_Res: ", "GPU Temp: " + (Double.parseDouble(temp0) + Double.parseDouble(temp1)) / 2);
        }

        // 匹配CPU总使用率
        String patternStr5 = "cpu\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)";
        Pattern pattern5 = Pattern.compile(patternStr5);
        Matcher matcher5 = pattern5.matcher(string);
        if (matcher5.find()) {
            int cpuUser = Integer.parseInt(matcher5.group(1));
            int cpuNice = Integer.parseInt(matcher5.group(2));
            int cpuSystem = Integer.parseInt(matcher5.group(3));
            int cpuIdle = Integer.parseInt(matcher5.group(4));
            int cpuIowait = Integer.parseInt(matcher5.group(5));
            int cpuIrq = Integer.parseInt(matcher5.group(6));
            int cpuSoftirq = Integer.parseInt(matcher5.group(7));
            int cpuSteal = Integer.parseInt(matcher5.group(8));
            int cpuGuest = Integer.parseInt(matcher5.group(9));
            int cpuGnice = Integer.parseInt(matcher5.group(10));
            int now = cpuUser + cpuNice + cpuSystem + cpuIdle + cpuIowait + cpuIrq + cpuSoftirq + cpuSteal + cpuGuest + cpuGnice;
            int interval = now - cpuLast[8];
            double cpuUsage = 1 - (cpuIdle - cpuLastIdle[8]) / (interval + 0.0);
            cpuLast[8] = now;
            cpuLastIdle[8] = cpuIdle;
            metrics[9].setUsage(cpuUsage);
            Log.d("Monitor_Res: ", "CPU Usage: " + cpuUsage);
        }

        // 如果出现Security exception: Package unknown does not belong to 0，说明温度匹配出错，换新的命令
        if (string.contains("Security exception: Package unknown does not belong to 0")) {
            try{
                stream.write(" dumpsys thermalservice | grep -A 3 \"Current temperatures from HAL:\"\n");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 匹配温度Temperature{mValue=31.155, mType=0, mName=CPU, mStatus=0}
        String patternStrTemp = "mValue=(\\d+\\.\\d+),\\s+mType=(\\d+),\\s+mName=(.*?),\\s+mStatus=(\\d+)";
        Pattern patternTemp = Pattern.compile(patternStrTemp);
        Matcher matcherTemp = patternTemp.matcher(string);
        while (matcherTemp.find()) {
            String temp = matcherTemp.group(1);
            String name = matcherTemp.group(3);
            if (name.equals("CPU")) {
                metrics[9].setTemp(Double.parseDouble(temp));
                Log.d("Monitor_Res: ", "CPU Temp: " + temp);
            }else if (name.equals("GPU")) {
                metrics[8].setTemp(Double.parseDouble(temp));
                Log.d("Monitor_Res: ", "GPU Temp: " + temp);
            }
        }
    }

    // stream的getter方法
    public AdbStream getStream() {
        return stream;
    }

    public void closeStream() {
        // 清空
        updateWindows(metrics);
        // 关闭stream
        try {
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        // 关闭adbConnection
        try {
            adbConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 该方法用于更新窗口
    private void updateWindows(metrics[] metrics) {
        // 更新窗口
        Intent intent = new Intent("com.wwh.UPDATE_ACTION");
        intent.putExtra("data_array", metrics);
        context.sendBroadcast(intent);
    }
}
