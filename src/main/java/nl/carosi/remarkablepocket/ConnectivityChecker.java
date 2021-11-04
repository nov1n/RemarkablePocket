package nl.carosi.remarkablepocket;

import java.io.IOException;
import java.net.URL;
import java.util.function.Consumer;

final class ConnectivityChecker {
    private static final int RETRY_INTERVAL = 5000;

    static void ensureConnected(Consumer<String> out) {
        while (true) {
            try {
                new URL("http://www.google.com").openConnection().connect();
                break;
            } catch (IOException e) {
                out.accept(
                        "Unable to connect to the internet. Please check your internet connection.");
                try {
                    Thread.sleep(RETRY_INTERVAL);
                } catch (InterruptedException e2) {
                    throw new RuntimeException("Thread interrupted");
                }
            }
        }
    }
}
