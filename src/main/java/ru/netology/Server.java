package ru.netology;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    final String STATUS_OK = "200 OK";
    final String STATUS_NOT_FOUND = "404 Not Found";
    final String STATUS_BAD_REQUEST = "400 Bad Request";
    final String STATUS_SERVICE_UNAVAILABLE = "503 Service Unavailable";
    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private final int PORT;
    private final ExecutorService threadPool;
    private final ConcurrentHashMap<String, Map<String, Handler>> handlers;


    public Server(int PORT) {
        this.PORT = PORT;
        threadPool = Executors.newFixedThreadPool(64);
        handlers = new ConcurrentHashMap<>();
    }

    public void start() {
        try (final var serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started!");
            while (!serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                System.out.printf("New connection accepted. Port: %d%n", clientSocket.getPort());
                threadPool.execute(() -> connection(clientSocket));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            threadPool.shutdown();
        }
    }

    private void connection(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                return;
            }

            final var path = parts[1];
            final String method = parts[0];

            if (!handlers.containsKey(method)) {
                responseWithoutContent(out, STATUS_BAD_REQUEST);
                return;
            }

            Request request = new Request(method, path);

            Map<String, Handler> handlerMap = handlers.get(method);
            if (handlerMap.containsKey(path)) {
                Handler handler = handlerMap.get(path);
                handler.handle(request, out);
            } else {
                if (!validPaths.contains(path)) {
                    responseWithoutContent(out, STATUS_NOT_FOUND);
                } else {
                    final var filePath = Path.of(".", "public", path);
                    final var mimeType = Files.probeContentType(filePath);

                    // special case for classic
                    if (path.equals("/classic.html")) {
                        final var template = Files.readString(filePath);
                        final var content = template.replace(
                                "{time}",
                                LocalDateTime.now().toString()
                        ).getBytes();
                        out.write((
                                "HTTP/1.1 " + STATUS_OK + "\r\n" +
                                        "Content-Type: " + mimeType + "\r\n" +
                                        "Content-Length: " + content.length + "\r\n" +
                                        "Connection: close\r\n" +
                                        "\r\n"
                        ).getBytes());
                        out.write(content);
                        out.flush();
                        return;
                    }

                    final var length = Files.size(filePath);
                    out.write((
                            "HTTP/1.1 " + STATUS_OK + "\r\n" +
                                    "Content-Type: " + mimeType + "\r\n" +
                                    "Content-Length: " + length + "\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n"
                    ).getBytes());
                    Files.copy(filePath, out);
                    out.flush();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    void addHandler(String method, String path, Handler handler) {
        if (!handlers.containsKey(method)) {
            handlers.put(method, new HashMap<>());
        }
        handlers.get(method).put(path, handler);
    }

    void responseWithoutContent(BufferedOutputStream out, String responseStatus) throws IOException {
        out.write((
                "HTTP/1.1 " + responseStatus + "\r\n" +
                        "Content-Length: 0\r\n" +
                        "Connection: close\r\n" +
                        "\r\n"
        ).getBytes());
        out.flush();
    }


}

