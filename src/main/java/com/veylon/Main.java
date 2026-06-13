package com.veylon;

public final class Main {

    public static void main(String[] args) {
        System.out.println("VEYLON: Deep Frontier - starting...");
        try {
            new Game().run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
