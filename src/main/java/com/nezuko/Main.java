package com.nezuko;


import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        CrptApi api = new CrptApi(5, TimeUnit.SECONDS, "asdasdad", "http://localhost:8080");
    }

    public static void print(int n) {

    }
}