package com.example.todoapp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
public class FileStorageService {
    private final AttachmentMapper mapper;
    private final TransactionTemplate transactions;
    private final Path directory;

    public FileStorageService(AttachmentMapper mapper, PlatformTransactionManager transactionManager,
            @Value("${app.upload.dir:./uploads}") String uploadDir) throws IOException {
        this.mapper = mapper;
        this.transactions = new TransactionTemplate(transactionManager);
        this.directory = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
    }

    public TodoAttachment store(Long todoId, MultipartFile file) throws IOException {
        var checked = AttachmentPolicy.check(file);
        String storedName = UUID.randomUUID() + "." + checked.extension();
        Path target = resolve(storedName);
        AtomicBoolean created = new AtomicBoolean();
        try {
            return transactions.execute(status -> {
                requireActiveTodo(todoId);
                try {
                    Files.createFile(target);
                    created.set(true);
                    Files.write(target, checked.bytes(), StandardOpenOption.WRITE);
                } catch (IOException failed) {
                    throw new UncheckedIOException(failed);
                }
                TodoAttachment attachment = new TodoAttachment();
                attachment.setTodoId(todoId);
                attachment.setOriginalFilename(checked.filename());
                attachment.setStoredFilename(storedName);
                attachment.setContentType(checked.contentType());
                attachment.setFileSize((long) checked.bytes().length);
                mapper.insert(attachment);
                return attachment;
            });
        } catch (RuntimeException failed) {
            if (created.get()) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanupFailed) {
                    failed.addSuppressed(cleanupFailed);
                }
            }
            throw failed;
        }
    }

    public record Download(TodoAttachment attachment, Path path) {}

    public Download download(Long todoId, Long attachmentId) {
        TodoAttachment attachment = mapper.findVisibleById(todoId, attachmentId);
        if (attachment == null) throw notFound();
        Path path = resolve(attachment.getStoredFilename());
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) throw notFound();
        return new Download(attachment, path);
    }

    public void delete(Long todoId, Long attachmentId) {
        AtomicReference<Path> original = new AtomicReference<>();
        AtomicReference<Path> staged = new AtomicReference<>();
        try {
            transactions.executeWithoutResult(status -> {
                requireActiveTodo(todoId);
                TodoAttachment attachment = mapper.findVisibleById(todoId, attachmentId);
                if (attachment == null) throw notFound();
                Path source = resolve(attachment.getStoredFilename());
                // Retain the bytes until the database transaction has committed.
                if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                    Path pendingDelete = directory.resolve(UUID.randomUUID() + ".deleted");
                    try {
                        Files.move(source, pendingDelete);
                        original.set(source);
                        staged.set(pendingDelete);
                    } catch (IOException failed) {
                        throw new UncheckedIOException(failed);
                    }
                }
                if (mapper.delete(todoId, attachmentId) != 1) throw notFound();
            });
        } catch (RuntimeException failed) {
            if (staged.get() != null) {
                try {
                    Files.move(staged.get(), original.get());
                } catch (IOException restoreFailed) {
                    failed.addSuppressed(restoreFailed);
                }
            }
            throw failed;
        }
        if (staged.get() != null) {
            try {
                Files.deleteIfExists(staged.get());
            } catch (IOException cleanupFailed) {
                log.warn("削除済み添付ファイルの一時データを消せませんでした: {}", staged.get(), cleanupFailed);
            }
        }
    }

    private void requireActiveTodo(Long todoId) {
        if (mapper.lockActiveTodo(todoId) == null) throw notFound();
    }

    private Path resolve(String name) {
        if (name == null || !name.matches("[a-f0-9-]{36}\\.[a-z0-9]+")) throw notFound();
        Path path = directory.resolve(name).normalize();
        if (!path.getParent().equals(directory)) throw notFound();
        return path;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "予定または添付ファイルが見つかりません。");
    }
}
