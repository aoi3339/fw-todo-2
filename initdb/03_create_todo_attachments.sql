CREATE TABLE todo_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    todo_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(80) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_attachments_todo (todo_id),
    CONSTRAINT fk_attachments_todo FOREIGN KEY (todo_id) REFERENCES todos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
