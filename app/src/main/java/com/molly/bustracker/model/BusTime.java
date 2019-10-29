package com.molly.bustracker.model;

public class BusTime {

    String duration;
    String name;

    public BusTime() {
    }

    public BusTime(String duration, String name) {
        this.duration = duration;
        this.name = name;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
