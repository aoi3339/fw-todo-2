package com.example.todoapp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/todos/{todoId}/attachments")
@Slf4j
public class AttachmentController {
    private final TodoService todos;
    private final AttachmentMapper attachments;
    private final FileStorageService storage;

    public AttachmentController(TodoService todos, AttachmentMapper attachments, FileStorageService storage) {
        this.todos = todos;
        this.attachments = attachments;
        this.storage = storage;
    }

    @GetMapping
    public String list(@PathVariable Long todoId, Model model) {
        Todo todo = todos.findById(todoId);
        if (todo == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "予定が見つかりません。");
        model.addAttribute("todo", todo);
        model.addAttribute("attachments", attachments.findByTodoId(todoId));
        return "attachments";
    }

    @PostMapping
    public String upload(@PathVariable Long todoId, @RequestParam("file") MultipartFile file,
            RedirectAttributes redirect) {
        try {
            storage.store(todoId, file);
            redirect.addFlashAttribute("attachmentMessage", "ファイルを添付しました。");
        } catch (ResponseStatusException missing) {
            throw missing;
        } catch (IllegalArgumentException invalid) {
            redirect.addFlashAttribute("attachmentError", invalid.getMessage());
        } catch (IOException | RuntimeException failed) {
            log.error("添付ファイルの保存に失敗しました todoId={}", todoId, failed);
            redirect.addFlashAttribute("attachmentError", "ファイルを保存できませんでした。時間をおいてもう一度お試しください。");
        }
        return "redirect:/todos/" + todoId + "/attachments";
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long todoId, @PathVariable Long attachmentId)
            throws IOException {
        var download = storage.download(todoId, attachmentId);
        String disposition = ContentDisposition.attachment()
                .filename(download.attachment().getOriginalFilename(), StandardCharsets.UTF_8).build().toString();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(Files.size(download.path())).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(new PathResource(download.path()));
    }

    @PostMapping("/{attachmentId}/delete")
    public String delete(@PathVariable Long todoId, @PathVariable Long attachmentId, RedirectAttributes redirect) {
        try {
            storage.delete(todoId, attachmentId);
            redirect.addFlashAttribute("attachmentMessage", "添付ファイルを削除しました。");
        } catch (ResponseStatusException missing) {
            throw missing;
        } catch (RuntimeException failed) {
            log.error("添付ファイルの削除に失敗しました attachmentId={}", attachmentId, failed);
            redirect.addFlashAttribute("attachmentError", "ファイルを削除できませんでした。時間をおいてもう一度お試しください。");
        }
        return "redirect:/todos/" + todoId + "/attachments";
    }
}
