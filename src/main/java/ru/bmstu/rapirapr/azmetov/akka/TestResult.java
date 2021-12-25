package ru.bmstu.rapirapr.azmetov.akka;

public class TestResult {
    private final String url;
    private final Integer time;
    private final Boolean isNew;

    public TestResult(String url, Integer time) {
        this.url = url;
        this.time = time;
        this.isNew = false;
    }

    public TestResult(String url, Integer time, Boolean isNew) {
        this.url = url;
        this.time = time;
        this.isNew = isNew;
    }


    public String getUrl() {
        return url;
    }

    public Integer getTime() {
        return time;
    }

    public Boolean getIsNew() {
        return isNew;
    }

    @Override
    public String toString() {
        return "TestResult{" +
                ", status='" + this.getUrl() + '\'' +
                ", output='" + this.getTime() + '\'' +
                '}';
    }
}
