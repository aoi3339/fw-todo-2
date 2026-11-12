# ToDoアプリ DB設計書

## 1. この文書の目的

この文書は、ToDoアプリで扱うデータをMySQLへどのように保存するかを定めます。

- DB（データベース）: アプリのデータを保存する仕組み
- テーブル: データを表の形で保存する場所
- カラム: テーブルの縦の項目（列）
- レコード: やること1件分のデータ（行）

設計の根拠は `handout_app_spec.md` と `requirements.md` です。画面の配置や見た目は、別に作成する画面設計書で定めます。

## 2. 使用するデータベース

| 項目 | 設計内容 |
|---|---|
| DB製品 | MySQL 8.0 |
| データベース名 | `todoapp` |
| テーブル数 | 1 |
| テーブル名 | `todos` |
| 1レコードの意味 | やること1件 |
| 文字コード | `utf8mb4`（日本語などを保存できる文字コード） |

## 3. テーブル概要

| 論理名（人が読む名前） | 物理名（DBで使う名前） | 用途 |
|---|---|---|
| やること | `todos` | やること、メモ、分類、期限、状態などを保存する |

本アプリは利用者1人で使用し、ログイン機能を持ちません。そのため、利用者を識別するカラムは設けません。

## 4. カラム設計

| # | 項目 | カラム名 | データ型 | NULL | 既定値 | 制約・説明 |
|---:|---|---|---|---|---|---|
| 1 | ID | `id` | `BIGINT` | 不可 | 自動採番 | 主キー。レコードを見分ける番号。1から自動で増える |
| 2 | やること | `title` | `VARCHAR(255)` | 不可 | なし | 255文字以内。空白だけの入力もアプリ側で不可とする |
| 3 | メモ | `detail` | `VARCHAR(255)` | 可 | `NULL` | 255文字以内。未入力を許可する |
| 4 | ジャンル | `category` | `VARCHAR(255)` | 不可 | なし | 指定された5種類のいずれかに限定する |
| 5 | 優先度 | `priority` | `INT` | 不可 | `2` | `1`＝高、`2`＝中、`3`＝低 |
| 6 | 期限 | `due_date` | `DATE` | 可 | `NULL` | 未入力を許可する |
| 7 | 完了状態 | `completed` | `BOOLEAN` | 不可 | `FALSE` | `TRUE`＝完了、`FALSE`＝未完了 |
| 8 | 登録日時 | `created_at` | `DATETIME` | 不可 | 現在日時 | レコードの登録時にMySQLが自動設定する |
| 9 | 更新日時 | `updated_at` | `DATETIME` | 不可 | 現在日時 | 登録時にMySQLが設定し、更新時にも自動更新する |

`NULL` は「値が入っていない状態」です。「NULL不可」は、必ず値を保存するという意味です。既定値は、値が指定されなかった場合に自動で使う値です。

## 5. キーと制約

- 主キー（各レコードを重複なく見分ける印）は `id` です。
- `id` は `AUTO_INCREMENT`（保存するたびに番号を1ずつ自動で増やす仕組み）を使用します。
- `category` は次の5種類だけを保存できます。
  - `デザイン`
  - `マーケティング`
  - `プログラミング`
  - `資格`
  - `就職活動`
- `priority` は `1`、`2`、`3` のいずれかだけを保存できます。
- 外部キー（別テーブルとのつながりを示す設定）はありません。使用するテーブルが `todos` だけだからです。

## 6. 要件とカラムの対応

| 要件 | 対応カラム | 対応内容 | 根拠 |
|---|---|---|---|
| 1件を見分ける番号を自動で割り当てる | `id` | 主キー、自動採番、利用者は入力しない | `handout_app_spec.md` 2、`requirements.md` 5 |
| やることの名前を保存する | `title` | 必須、255文字以内 | `handout_app_spec.md` 2・5-1、`requirements.md` 5・7 |
| メモを保存する | `detail` | 任意、255文字以内 | `handout_app_spec.md` 2・5-1、`requirements.md` 5・7 |
| ジャンルを5種類から選ぶ | `category` | 必須、5種類に限定 | `handout_app_spec.md` 2、`requirements.md` 5・7 |
| 優先度を高・中・低で管理する | `priority` | 必須、1＝高・2＝中・3＝低、DBの既定値は2 | `handout_app_spec.md` 2・4・5-1、`requirements.md` 4.1・5・7 |
| 期限を保存する | `due_date` | 任意の日付 | `handout_app_spec.md` 2・5-1、`requirements.md` 5・7 |
| 完了・未完了を管理する | `completed` | 既定値は未完了。編集時に変更できる | `handout_app_spec.md` 2・4、`requirements.md` 4.1・4.3・5 |
| 登録した日時を自動で記録する | `created_at` | 登録時にMySQLが現在日時を設定する | `handout_app_spec.md` 2、`requirements.md` 5 |
| 最後に編集した日時を自動で記録する | `updated_at` | 登録時と更新時にMySQLが現在日時を設定する | `handout_app_spec.md` 2、`requirements.md` 5 |
| 名前に入力語を含むデータを検索する | `title` | `title` の部分一致で絞り込む | `handout_app_spec.md` 5-4、`requirements.md` 4.5・8 |
| ジャンルが一致するデータを検索する | `category` | `category` の完全一致で絞り込む | `handout_app_spec.md` 5-4、`requirements.md` 4.5・8 |
| 期限が近い順・遠い順に並べる | `due_date` | `due_date` を昇順・降順で並べる | `handout_app_spec.md` 5-4、`requirements.md` 4.6・8 |
| 登録・編集・削除のログに対象番号を残す | `id` | 操作したレコードの `id` をアプリのログへ出す | `handout_app_spec.md` 5-5、`requirements.md` 10 |

画面上の必須選択とDBの既定値は役割が異なります。登録画面では利用者に優先度を必ず選んでもらいますが、DB側では仕様どおり、値が渡されなかった場合の安全な既定値を `2`（中）とします。

## 7. CREATE TABLE文（DDL）

DDLは、テーブルの形を作るためのSQL命令です。次のSQLで `todos` テーブルを作成します。

```sql
CREATE TABLE todos (
    id BIGINT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) NOT NULL,
    detail VARCHAR(255) NULL,
    category VARCHAR(255) NOT NULL,
    priority INT NOT NULL DEFAULT 2,
    due_date DATE NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
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

- `CHECK` は、決められた値以外を保存させない確認ルールです。
- `CURRENT_TIMESTAMP` は、保存した時点の現在日時です。
- `ON UPDATE CURRENT_TIMESTAMP` は、レコードを更新したときに更新日時を自動で入れ直す設定です。
- `InnoDB` は、MySQLでデータを安全に保存・更新するための標準的な保存方式です。

## 8. 登録・更新・削除時の扱い

| 操作 | 主なカラムの扱い |
|---|---|
| 登録 | `title`、`detail`、`category`、`priority`、`due_date` を受け取る。`id`、`created_at`、`updated_at` はMySQLが設定し、`completed` は未指定なら未完了になる |
| 編集 | 登録時の5項目と `completed` を更新する。`updated_at` はMySQLが自動更新する |
| 削除 | `id` で対象を特定し、そのレコードを物理削除（DBから実際に消すこと）する |

削除したデータを戻す機能は対象外のため、削除済みを示すカラムは設けません。また、完了した日時を記録する機能も対象外のため、完了日時のカラムは設けません。

## 9. インデックス設計

インデックスは、データを探しやすくする索引です。今回は小規模な1人用アプリであり、主キー `id` のインデックスだけを使用します。検索や並び替え用の追加インデックスは、データ量や速度上の問題が確認された場合に検討します。

## 10. 設計上の補足

- `title` の空白だけの入力や文字数は、保存前にアプリ側でも確認します。DBのデータ型でも最大255文字に制限します。
- `detail` と `due_date` は任意項目なので、未入力時は `NULL` とします。
- 操作ログはアプリの動作記録として出力し、`todos` テーブル内には保存しません。テーブルは1つだけという前提を守るためです。
- 一覧のメッセージや検索条件は一時的な画面情報であり、`todos` テーブルには保存しません。
- 画面の構造や見た目は本書の対象外です。
