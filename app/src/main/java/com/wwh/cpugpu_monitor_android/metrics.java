package com.wwh.cpugpu_monitor_android;

import android.os.Parcel;
import android.os.Parcelable;

public class metrics implements Parcelable {
    private String name;
    private int freq;
    private double usage;
    private double temp;
    private int time;

    private int memo;

    public metrics() {
        this.freq = -1;
        this.usage = -1.0;
        this.temp = -100;
        this.memo = -1;
    }

    protected metrics(Parcel in) {
        name = in.readString();
        freq = in.readInt();
        usage = in.readDouble();
        temp = in.readDouble();
        time = in.readInt();
        memo = in.readInt();
    }

    public static final Creator<metrics> CREATOR = new Creator<metrics>() {
        @Override
        public metrics createFromParcel(Parcel in) {
            return new metrics(in);
        }

        @Override
        public metrics[] newArray(int size) {
            return new metrics[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeInt(freq);
        dest.writeDouble(usage);
        dest.writeDouble(temp);
        dest.writeInt(time);
        dest.writeInt(memo);
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public double getUsage() {
        return usage;
    }

    public void setUsage(double usage) {
        this.usage = usage;
    }

    public double getTemp() {
        return temp;
    }

    public void setTemp(double temp) {
        this.temp = temp;
    }

    public int getTime() {
        return time;
    }

    public void setTime(int time) {
        this.time = time;
    }

    public int getMemo() {
        return memo;
    }

    public void setMemo(int memo) {
        this.memo = memo;
    }
}
