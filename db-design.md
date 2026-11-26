# ToDoアプリ DB設計書

更新日：2026年11月26日。初期の「todosだけ・ログインなし・行を消す削除」から、現在の実装へ更新しました。根拠は [要件定義書](requirements.md) と `initdb/*.sql`、`src/main/resources/mapper`、各Mapperです。

## 1. 構成と用語

DB（データの保存先）はMySQL 8.0、データベース名は `todoapp`、文字コードは日本語などを保存できる `utf8mb4` です。

- テーブル：表の形の保存先。カラム：表の列。レコード：表の1行。
- 主キー：各行を見分ける番号。外部キー：別の表とのつながりを示す制約。
- `NULL`：値なし。既定値：値を指定しなかったときに使う値。

| テーブル | 1行の意味 | 用途 |
|---|---|---|
| `todos` | やること1件 | 名前・メモ・分類・優先度・期限・完了・削除の状態 |
| `users` | ログイン用ユーザー1人 | ユーザー名とパスワードのハッシュなど |
| `todo_attachments` | 添付ファイル1件の情報 | 所属するやること、元の名前、保存名、種類、容量 |

利用者は1人です。`todos` にユーザーIDはなく、ログイン追加は複数人のデータ分離を意味しません。

## 2. todos

| 列 | 型 | NULL | 既定値・説明 |
|---|---|---|---|
| `id` | BIGINT | 不可 | 自動採番・主キー |
| `title` | VARCHAR(255) | 不可 | やることの名前。アプリで必須・255文字以内を確認 |
| `detail` | VARCHAR(255) | 可 | メモ。未入力を許可 |
| `category` | VARCHAR(255) | 不可 | 決められた5種類 |
| `priority` | INT | 不可 | 既定値2。1＝高、2＝中、3＝低 |
| `due_date` | DATE | 可 | 期限。未入力を許可 |
| `completed` | BOOLEAN | 不可 | 既定値FALSE（未完了） |
| `deleted_at` | DATETIME | 可 | 既定値NULL。削除時の日時、復元時はNULL |
| `created_at` | DATETIME | 不可 | 登録時に現在日時 |
| `updated_at` | DATETIME | 不可 | 登録時と更新時に現在日時 |

ジャンルはデザイン・マーケティング・プログラミング・資格・就職活動に限定します。ジャンルと優先度はDBのCHECK（決められた値だけを受け付ける制約）でも確認します。登録画面では両方を空欄から選択します。

完了日時の `completed_at` はありません。完了状態は既存の `completed` だけで管理します。

## 3. users

| 列 | 型 | NULL | 既定値・説明 |
|---|---|---|---|
| `id` | BIGINT | 不可 | 自動採番・主キー |
| `username` | VARCHAR(50) | 不可 | 重複不可。大文字小文字を区別する設定 |
| `password` | VARCHAR(255) | 不可 | BCryptで作るハッシュ（元に戻せない変換結果）。原文は保存しない |
| `role` | VARCHAR(20) | 不可 | 既定値USER |
| `created_at` | TIMESTAMP | 不可 | 登録時に現在日時 |

初回設定はユーザーが0人のときだけ許可し、アプリは最初のユーザーをID 1で登録します。ユーザー名は空白なし・50文字以内、パスワードは8文字以上かつUTF-8で72バイト以内で確認用入力と一致させます。これらの入力確認はアプリ側で行います。

## 4. todo_attachmentsとファイル本体

| 列 | 型 | NULL | 既定値・説明 |
|---|---|---|---|
| `id` | BIGINT | 不可 | 自動採番・主キー |
| `todo_id` | BIGINT | 不可 | `todos.id` を参照する外部キー |
| `original_filename` | VARCHAR(255) | 不可 | 利用者に表示・ダウンロードする元の名前 |
| `stored_filename` | VARCHAR(80) | 不可 | 重複不可。UUID（重複しにくい識別子）を使った保存名 |
| `content_type` | VARCHAR(100) | 不可 | ファイルの種類 |
| `file_size` | BIGINT | 不可 | バイト単位の容量 |
| `uploaded_at` | DATETIME | 不可 | 添付時に現在日時 |

1件のやることに複数の添付情報が対応します。`todo_id` に検索用のインデックス（索引）があります。ファイル本体はDBに格納せず、Dockerでは `/app/uploads` に置き、`fw-upload-data` ボリュームに保存します。PCから直接起動する場合の既定先は `./uploads` で、`TODO_UPLOAD_DIR` で変更できます。

1ファイル10MBまで、1回1ファイルです。許可する種類はPNG・JPEG・GIF・WebP・PDF・UTF-8のTXT/CSV・マクロなしのdocx/xlsx/pptxで、名前と中身を確認します。保存先は公開フォルダーから分離し、認証されたダウンロード処理を通して取得します。

## 5. 登録・更新・削除・復元

| 操作 | 保存内容 |
|---|---|
| 登録 | 名前・メモ・ジャンル・優先度・期限を保存。完了は未指定ならFALSE。番号と日時は自動設定 |
| 編集 | 入力5項目と完了状態を更新。完了日時は記録しない |
| 完了ボタン | 未完了かつ未削除の場合だけ `completed=TRUE` に更新 |
| やることの削除 | `deleted_at=CURRENT_TIMESTAMP`。行と添付は残す（論理削除） |
| やることの復元 | `deleted_at=NULL` に戻す。完了状態は変更しない |
| 添付自体の削除 | 添付情報の行とファイル本体を消す（物理削除）。復元しない |

通常の一覧・件数・CSV・API・カレンダー・MCP・番号指定での取得は `deleted_at IS NULL` の条件で削除済みを除外します。削除済み一覧の件数と取得だけは `deleted_at IS NOT NULL` を使います。親が削除済みの間は添付の取得・追加・削除もできません。

一覧は同じ検索条件で件数を数え、`LIMIT 10 OFFSET ...` で必要なページだけ取得します。名前は部分一致、ジャンルは完全一致です。期限で並べ、同期限ではIDの昇順にします。CSVは同じ条件ですが件数制限を付けません。通常の `/api/todos` とMCPの一覧は完了済みを含み、期間指定があると期限のない項目を除外します。

メモの未入力はNULLまたは空文字として扱える列です。期限の未入力はNULLです。検索語・ページ番号・天気・祝日はDBへ保存しません。

## 6. 初期化と既存データ

- 作成用SQLは次章の3ファイルです。登録済みのユーザー名・パスワードや、固定のやること5件を同梱しません。
- Dockerの `initdb` は、DBの保存領域を初めて作るときだけ実行されます。
- 既存DBには、SQLファイルを変更しただけでは反映されません。既存データを維持する構造変更と、新規作成を区別します。
- 本書更新のためのDB初期化やデータの追加・削除は行いません。

## 7. 現在のテーブル作成SQL

以下は現在の `initdb` と同じ内容です。既存DBへそのまま再実行する手順ではありません。

### `initdb/01_create_table.sql`

```sql
CREATE TABLE todos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    detail VARCHAR(255) NULL,
    category VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 2,
    due_date DATE NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT chk_todos_category CHECK (
        category IN (
            'デザイン',
            'マーケティング',
            'プログラミング',
            '資格',
            '就職活動'
        )
    ),
    CONSTRAINT chk_todos_priority CHECK (priority IN (1, 2, 3))
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
```

### `initdb/02_create_users.sql`

```sql
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs;
```

### `initdb/03_create_todo_attachments.sql`

```sql
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
```
