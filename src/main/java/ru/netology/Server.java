package ru.netology;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URISyntaxException;
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
        try (final var in = new BufferedInputStream(socket.getInputStream());
             final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            Request request = Request.createRequest(in);
            // Check for bad requests and drop connection
            if (request == null || !handlers.containsKey(request.getMethod())) {
                responseWithoutContent(out, STATUS_BAD_REQUEST);
                return;
            } else {
                System.out.println("Request debug information: ");
                System.out.println("METHOD: " + request.getMethod());
                System.out.println("PATH: " + request.getPath());
                System.out.println("HEADERS: " + request.getHeaders());
                System.out.println("Query Params:");
                for (var para : request.getQueryParams()) {
                    System.out.println(para.getName() + " = " + para.getValue());
                }

                System.out.println("Test for dumb param name:");
                System.out.println(request.getQueryParam("YetAnotherDumb").getName());
                System.out.println("Test for dumb param name-value:");
                System.out.println(request.getQueryParam("testDebugInfo").getValue());
            }

            Map<String, Handler> handlerMap = handlers.get(request.getMethod());
            if (handlerMap.containsKey(request.getPath())) {
                Handler handler = handlerMap.get(request.getPath());
                handler.handle(request, out);
            } else {
                if (!validPaths.contains(request.getPath())) {
                    responseWithoutContent(out, STATUS_NOT_FOUND);
                } else {
                    final var filePath = Path.of(".", "public", request.getPath());
                    final var mimeType = Files.probeContentType(filePath);

                    // special case for classic
                    if (request.getPath().equals("/classic.html")) {
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
        } catch (IOException | URISyntaxException e) {
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

