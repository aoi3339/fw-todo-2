package com.example.todoapp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;

final class AttachmentPolicy {
    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"), Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"), Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"), Map.entry("csv", "text/csv"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    record CheckedFile(String filename, String extension, String contentType, byte[] bytes) {}

    static CheckedFile check(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/")
                || name.contains("\\") || name.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("ファイル名を確認してください（255文字以内、区切り文字や制御文字は使えません）。");
        }
        int dot = name.lastIndexOf('.');
        String extension = dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        if (!TYPES.containsKey(extension)) {
            throw new IllegalArgumentException("この種類のファイルは添付できません。画面に記載された種類を選んでください。");
        }
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("1ファイル10MB以内にしてください。");
        byte[] bytes;
        try (var input = file.getInputStream()) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        }
        if (bytes.length == 0) throw new IllegalArgumentException("空のファイルは添付できません。");
        if (bytes.length > MAX_BYTES) throw new IllegalArgumentException("1ファイル10MB以内にしてください。");
        boolean matches = switch (extension) {
            case "png" -> starts(bytes, new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
            case "jpg", "jpeg" -> starts(bytes, new byte[]{(byte) 255, (byte) 216, (byte) 255});
            case "gif" -> starts(bytes, "GIF87a") || starts(bytes, "GIF89a");
            case "webp" -> bytes.length >= 12 && starts(bytes, "RIFF")
                    && new String(bytes, 8, 4, StandardCharsets.US_ASCII).equals("WEBP");
            case "pdf" -> starts(bytes, "%PDF-");
            case "txt", "csv" -> isUtf8Text(bytes);
            default -> isOfficeDocument(bytes, extension);
        };
        if (!matches) throw new IllegalArgumentException("ファイルの中身と種類が一致しないか、対応していない形式です。テキスト・CSVはUTF-8で保存してください。");
        return new CheckedFile(name, extension, TYPES.get(extension), bytes);
    }

    private static boolean starts(byte[] bytes, String prefix) {
        return starts(bytes, prefix.getBytes(StandardCharsets.US_ASCII));
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }

    private static boolean isUtf8Text(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return text.codePoints().noneMatch(c -> Character.isISOControl(c) && c != '\t' && c != '\n' && c != '\r');
        } catch (CharacterCodingException invalid) {
            return false;
        }
    }

    private static boolean isOfficeDocument(byte[] bytes, String extension) {
        String document = switch (extension) {
            case "docx" -> "word/document.xml";
            case "xlsx" -> "xl/workbook.xml";
            default -> "ppt/presentation.xml";
        };
        Set<String> entries = new HashSet<>();
        long expandedSize = 0;
        int entryCount = 0;
        byte[] buffer = new byte[8192];
        // Inspect the package without extracting it; cap expanded data as well as upload size.
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > 2000) return false;
                String name = entry.getName();
                if (name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin")) return false;
                entries.add(name);
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expandedSize += read;
                    if (expandedSize > 50L * 1024 * 1024) return false;
                }
            }
            return entries.contains("[Content_Types].xml") && entries.contains(document);
        } catch (IOException invalid) {
            return false;
        }
    }
}
