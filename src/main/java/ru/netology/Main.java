package ru.netology;

public class Main {
    static final int PORT = 9999;
    public static void main(String[] args) {
        Server server = new Server(PORT);

        server.start();
    }
}