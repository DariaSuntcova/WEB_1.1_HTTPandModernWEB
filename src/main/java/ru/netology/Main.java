package ru.netology;

public class Main {
    static final int PORT = 9999;
    public static void main(String[] args) {
        Server server = new Server(PORT);

        server.addHandler("GET", "/messages", (request, responseStream) ->
                server.responseWithoutContent(responseStream, server.STATUS_NOT_FOUND));

        server.addHandler("POST", "/messages", (request, responseStream) ->
                server.responseWithoutContent(responseStream, server.STATUS_SERVICE_UNAVAILABLE));

        server.start();
    }
}