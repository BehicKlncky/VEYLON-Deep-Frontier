package com.veylon;

public final class Main {

    public static void main(String[] args) {
        String version = Main.class.getPackage().getImplementationVersion();
        System.out.println("VEYLON: Deep Frontier "
                + (version == null ? "(development)" : "v" + version)
                + " - starting...");
        try {
            new Game().run();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }
}
