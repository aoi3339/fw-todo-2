package com.example.todoapp;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FileStorageServiceTests {
    @TempDir Path directory;
    AttachmentMapper mapper;
    PlatformTransactionManager transactionManager;
    FileStorageService storage;

    @BeforeEach
    void setUp() throws Exception {
        mapper = mock(AttachmentMapper.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(mapper.lockActiveTodo(1L)).thenReturn(1L);
        storage = new FileStorageService(mapper, transactionManager, directory.toString());
    }

    @Test
    void japaneseNamesArePreservedButDiskNamesAreGeneratedAndContentTypeIsNotTrusted() throws Exception {
        var file = file("応募資料.txt", "image/png", "添付の内容です".getBytes(StandardCharsets.UTF_8));
        var attachment = storage.store(1L, file);
        assertEquals("応募資料.txt", attachment.getOriginalFilename());
        assertEquals("text/plain", attachment.getContentType());
        assertNotEquals(attachment.getOriginalFilename(), attachment.getStoredFilename());
        assertArrayEquals(file.getBytes(), Files.readAllBytes(directory.resolve(attachment.getStoredFilename())));
        verify(mapper).insert(attachment);
    }

    @Test
    void unsafeNamesUnsupportedTypesEmptyBinaryAndExcessiveFilesAreRejectedWithoutWriting() throws Exception {
        for (String name : new String[]{"../secret.txt", "C:\\secret.txt", "bad\nname.txt", "program.exe", "file.html"}) {
            assertThrows(IllegalArgumentException.class, () -> storage.store(1L, file(name, "text/plain", new byte[]{65})));
        }
        assertThrows(IllegalArgumentException.class, () -> storage.store(1L, file("empty.txt", "text/plain", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> storage.store(1L, file("fake.png", "image/png", new byte[]{65,66})));
        assertThrows(IllegalArgumentException.class, () -> storage.store(1L, file("binary.txt", "text/plain", new byte[]{0,1,2})));
        assertThrows(IllegalArgumentException.class, () -> storage.store(1L,
                file("large.txt", "text/plain", new byte[AttachmentPolicy.MAX_BYTES + 1])));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        verify(mapper, never()).insert(any());
    }

    @Test
    void officePackagesMustMatchTheirExtensionAndCannotContainVbaMacros() throws Exception {
        for (String[] type : new String[][]{{"docx","word/document.xml"}, {"xlsx","xl/workbook.xml"}, {"pptx","ppt/presentation.xml"}}) {
            byte[] bytes = office(type[1], false);
            assertDoesNotThrow(() -> AttachmentPolicy.check(file("document." + type[0], "application/octet-stream", bytes)));
        }
        assertThrows(IllegalArgumentException.class, () -> AttachmentPolicy.check(
                file("wrong.xlsx", "application/octet-stream", office("word/document.xml", false))));
        assertThrows(IllegalArgumentException.class, () -> AttachmentPolicy.check(
                file("macro.docx", "application/octet-stream", office("word/document.xml", true))));
    }

    @Test
    void databaseInsertOrCommitFailureRemovesNewDiskFile() throws Exception {
        doThrow(new DataAccessResourceFailureException("test insert failure")).when(mapper).insert(any());
        assertThrows(DataAccessResourceFailureException.class,
                () -> storage.store(1L, file("note.txt", "text/plain", new byte[]{65})));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
        reset(mapper);
        when(mapper.lockActiveTodo(1L)).thenReturn(1L);
        doThrow(new DataAccessResourceFailureException("test commit failure")).when(transactionManager).commit(any());
        assertThrows(DataAccessResourceFailureException.class,
                () -> storage.store(1L, file("note.txt", "text/plain", new byte[]{65})));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test
    void missingOrDeletedTodoCannotReceiveFilesAndMissingMetadataCannotBeDownloaded() throws Exception {
        when(mapper.lockActiveTodo(1L)).thenReturn(null);
        assertThrows(ResponseStatusException.class,
                () -> storage.store(1L, file("note.txt", "text/plain", new byte[]{65})));
        assertThrows(ResponseStatusException.class, () -> storage.download(1L, 7L));
        try (var files = Files.list(directory)) { assertEquals(0, files.count()); }
    }

    @Test
    void deletionCommitFailureRestoresOriginalFile() throws Exception {
        var attachment = storage.store(1L, file("note.txt", "text/plain", new byte[]{65}));
        when(mapper.findVisibleById(1L, 7L)).thenReturn(attachment);
        when(mapper.delete(1L, 7L)).thenReturn(1);
        doThrow(new DataAccessResourceFailureException("test commit failure")).when(transactionManager).commit(any());
        assertThrows(DataAccessResourceFailureException.class, () -> storage.delete(1L, 7L));
        assertArrayEquals(new byte[]{65}, Files.readAllBytes(directory.resolve(attachment.getStoredFilename())));
        try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
    }

    @Test
    void downloadNeverResolvesMetadataOutsideStorageDirectory() {
        TodoAttachment attachment = new TodoAttachment();
        attachment.setStoredFilename("../outside.txt");
        when(mapper.findVisibleById(1L, 7L)).thenReturn(attachment);
        assertThrows(ResponseStatusException.class, () -> storage.download(1L, 7L));
    }

    private MockMultipartFile file(String filename, String type, byte[] bytes) {
        return new MockMultipartFile("file", filename, type, bytes);
    }

    private byte[] office(String document, boolean macro) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String entry : macro ? new String[]{"[Content_Types].xml", document, "word/vbaProject.bin"}
                    : new String[]{"[Content_Types].xml", document}) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write("test".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
