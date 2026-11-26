package com.example.todoapp;

import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AttachmentHttpTests {
    static final Path directory = tempDirectory();
    @LocalServerPort int port;
    @Autowired TodoService todos;
    @Autowired AttachmentMapper attachments;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @MockitoBean UserMapper users;
    Todo todo;
    HttpClient client;
    String base;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.dir", directory::toString);
    }

    @BeforeEach
    void start() throws Exception {
        User account = new User();
        account.setUsername("attachment-http-check");
        account.setPassword(encoder.encode("test-only-password"));
        account.setRole("USER");
        when(users.countUsers()).thenReturn(1L);
        when(users.findByUsername(account.getUsername())).thenReturn(account);
        todo = new Todo();
        todo.setTitle("attachment-http-" + UUID.randomUUID());
        todo.setCategory("デザイン");
        todo.setPriority(2);
        todo.setCompleted(false);
        todos.create(todo);
        base = "http://localhost:" + port;
        client = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
        String csrf = csrf(get("/login").body());
        var login = postForm("/login", "username=attachment-http-check&password=test-only-password&_csrf=" + encode(csrf));
        assertEquals(302, login.statusCode());
        assertEquals("/todos", URI.create(login.headers().firstValue("location").orElseThrow()).getPath());
    }

    @AfterEach
    void cleanup() throws Exception {
        if (todo != null && todo.getId() != null) {
            jdbc.update("DELETE FROM todo_attachments WHERE todo_id = ?", todo.getId());
            jdbc.update("DELETE FROM todos WHERE id = ? AND title = ?", todo.getId(), todo.getTitle());
        }
        try (var files = Files.list(directory)) {
            for (Path path : files.toList()) Files.delete(path);
        }
    }

    @AfterAll
    static void removeDirectory() throws Exception { Files.delete(directory); }

    @Test
    void uploadListDownloadAndConfirmedDeleteWorkThroughRealHttpWithJapaneseFilename() throws Exception {
        byte[] content = "日本語の添付資料\n次の行".getBytes(StandardCharsets.UTF_8);
        assertEquals(302, upload("応募資料.txt", content, csrf(get(path()).body())).statusCode());
        var list = attachments.findByTodoId(todo.getId());
        assertEquals(1, list.size());
        var attachment = list.getFirst();
        assertEquals("応募資料.txt", attachment.getOriginalFilename());
        assertTrue(get(path()).body().contains("応募資料.txt"));
        var downloaded = client.send(HttpRequest.newBuilder(URI.create(base + downloadPath(attachment))).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, downloaded.statusCode());
        assertArrayEquals(content, downloaded.body());
        assertTrue(downloaded.headers().firstValue("content-disposition").orElseThrow().contains("filename*=UTF-8''"));
        assertEquals("nosniff", downloaded.headers().firstValue("x-content-type-options").orElseThrow());
        assertEquals("no-store", downloaded.headers().firstValue("cache-control").orElseThrow());
        assertEquals(302, postForm(path() + "/" + attachment.getId() + "/delete",
                "_csrf=" + encode(csrf(get(path()).body()))).statusCode());
        assertTrue(attachments.findByTodoId(todo.getId()).isEmpty());
        assertFalse(Files.exists(directory.resolve(attachment.getStoredFilename())));
        assertEquals(404, get(downloadPath(attachment)).statusCode());
    }

    @Test
    void deletedTodoHidesEveryAttachmentOperationAndRestoreMakesFileAvailableAgain() throws Exception {
        assertEquals(302, upload("note.txt", new byte[]{65}, csrf(get(path()).body())).statusCode());
        var attachment = attachments.findByTodoId(todo.getId()).getFirst();
        String csrf = csrf(get(path()).body());
        todos.delete(todo.getId());
        assertEquals(404, get(path()).statusCode());
        assertEquals(404, get(downloadPath(attachment)).statusCode());
        assertEquals(404, upload("other.txt", new byte[]{66}, csrf).statusCode());
        assertEquals(404, postForm(path() + "/" + attachment.getId() + "/delete", "_csrf=" + encode(csrf)).statusCode());
        assertTrue(Files.exists(directory.resolve(attachment.getStoredFilename())));
        assertTrue(todos.restore(todo.getId()));
        assertEquals(200, get(downloadPath(attachment)).statusCode());
        assertEquals(1, attachments.findByTodoId(todo.getId()).size());
        assertEquals(404, get("/todos/0/attachments/" + attachment.getId() + "/download").statusCode());
    }

    @Test
    void unauthenticatedAndForgedRequestsCannotAccessOrChangeAttachments() throws Exception {
        upload("note.txt", new byte[]{65}, csrf(get(path()).body()));
        var attachment = attachments.findByTodoId(todo.getId()).getFirst();
        var anonymous = HttpClient.newHttpClient();
        for (String route : new String[]{path(), downloadPath(attachment)}) {
            var response = anonymous.send(HttpRequest.newBuilder(URI.create(base + route)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(302, response.statusCode());
            assertTrue(response.headers().firstValue("location").orElseThrow().endsWith("/login"));
        }
        assertEquals(403, upload("forged.txt", new byte[]{65}, "invalid-token").statusCode());
        assertEquals(403, postForm(path() + "/" + attachment.getId() + "/delete", "").statusCode());
        assertEquals(1, attachments.findByTodoId(todo.getId()).size());
    }

    @Test
    void oversizeFileShowsFriendlyErrorAndInvalidTypesLeaveNoMetadata() throws Exception {
        String token = csrf(get(path()).body());
        var oversize = upload("big.pdf", new byte[AttachmentPolicy.MAX_BYTES + 1], token);
        assertEquals(413, oversize.statusCode());
        assertTrue(oversize.body().contains("10MB"), oversize.body());
        assertTrue(attachments.findByTodoId(todo.getId()).isEmpty());
        assertEquals(302, upload("program.exe", new byte[]{65}, csrf(get(path()).body())).statusCode());
        assertTrue(get(path()).body().contains("この種類のファイルは添付できません"));
        assertTrue(attachments.findByTodoId(todo.getId()).isEmpty());
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    private String path() { return "/todos/" + todo.getId() + "/attachments"; }
    private String downloadPath(TodoAttachment file) { return path() + "/" + file.getId() + "/download"; }
    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private HttpResponse<String> postForm(String path, String form) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private HttpResponse<String> upload(String filename, byte[] content, String csrf) throws Exception {
        String boundary = "upload-check-" + UUID.randomUUID();
        var body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"_csrf\"\r\n\r\n" + csrf
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return client.send(HttpRequest.newBuilder(URI.create(base + path()))
                .header("Accept", "text/html")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
    private String csrf(String html) {
        var matcher = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
        assertTrue(matcher.find(), "CSRF field must be present");
        return matcher.group(1);
    }
    private String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8); }
    private static Path tempDirectory() {
        try { return Files.createTempDirectory("todo-attachment-http-"); }
        catch (Exception failed) { throw new ExceptionInInitializerError(failed); }
    }
}
