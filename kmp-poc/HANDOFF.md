# kuery-client v2 (KMP / sqlx4k) PoC — 引き継ぎメモ

> PoC ブランチ `poc/kmp-native-compiler-plugin` の作業記録。別セッション/別担当が続きから
> 作業するための脳内ダンプ。本 PR を切る際にはこのファイルは削除する前提の作業ドキュメント。
> 最終更新: 2026-07-22

## 何をしているか（背景）

kuery-client v2 として [sqlx4k](https://github.com/smyrgeorge/sqlx4k)（KMP の SQL クライアント、
Native は Rust sqlx への FFI、JVM は r2dbc/sqlite-jdbc 委譲）をバックエンドに追加できるかの
技術検証。結論: **技術リスクは全て解消済み。残りは API 設計判断のみ。**

ユーザー決定事項:
- **sqlx4k バックエンドに Micrometer observation は載せない** → core KMP 化時は observation
  パッケージを expect/actual にせず jvmMain に移すだけでよい
- sqlx4k の `@Table("name")` は不採用（テーブル名不要・CRUD 生成の巻き添えが嫌）
- 行マッピングは **compiler plugin による call-site 合成が本命**（第5弾）。KSP 版（第4弾）は
  比較用に併存させているが、本実装では捨てる方向

## PoC の 5 段階（コミット順）

| commit | 内容 | 結果 |
|---|---|---|
| 31c2069 | core DSL を同一 FQN で `kmp-poc` commonMain にコピー、compiler plugin を Native に適用 | plugin 無改造で Native 動作。5/5 green |
| 6141dbc | `Sqlx4kKueryClient`/`Sqlx4kFetchSpec`(single/singleOrNull/list/rowsUpdated) + 実 SQLite E2E | `:pN` は sqlx4k の named-param 構文そのまま。ブリッジは bind ループのみ |
| 5e13694 | sqlx4k `RowMapper<T>` 受けオーバーロード + sqlx4k-codegen(KSP) の `@Table` 生成 mapper | 動くが @Table(name)+@Id が必要で UX が重い |
| c3f05cf | 引数なし `@Record` + 自前 KSP プロセッサ (`:kmp-poc:codegen`, ~100行) に置換 | 動く。ただし利用者に KSP 配線負担 |
| 970ebd5 | **`RowMapperSynthesisTransformer`（IR 第3パス）: `.list<User>()` だけで mapper 合成** | アノテーション/KSP/リフレクション全部不要。本命 |

テストは各ターゲット 12/12 green（`StringInterpolationPocTest` 5 + `Sqlx4kKueryClientPocTest` 7）。
compiler モジュールの既存 unit/functional-test/ktlint/detekt も全通過。

## 変更ファイルの地図

- `kmp-poc/` — 使い捨て PoC モジュール（conventions 不適用、KMP: jvm + macosArm64）
  - `src/commonMain/.../kuery/core/` — core DSL のコピー（`@Language` と `@JvmField` だけ除去）
  - `src/commonMain/.../kuery/sqlx4k/Sqlx4kKueryClient.kt` — sqlx4k バックエンドの試作クライアント。
    reified マーカー（`single<T>()` 等）は plugin 未適用だと実行時エラーを投げる add/unaryPlus 方式
  - `src/commonMain/.../kuery/annotation/Record.kt` + `codegen/` — KSP 版（比較用、捨て予定）
  - `src/commonTest/` — 全シナリオのテスト
- `kuery-client-compiler/src/main/.../ir/RowMapperSynthesisTransformer.kt` — **新規 IR パス（PoC）**。
  `KueryClientIrGenerationExtension` に第3パスとして登録済み。production モジュールに PoC コードが
  入っている状態なので、本実装時はここを整備するか一旦 revert する
- `settings.gradle.kts` — `include("kmp-poc")` / `include("kmp-poc:codegen")` 追加

## 主要な技術的発見（ハマりどころ）

1. **compiler plugin は無改造で Kotlin/Native で動く**。FIR+IR・FQN マッチは backend 非依存。
   適用は functional-test と同じ方式の KMP 版:
   `configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspath") }` に
   `project(":kuery-client-compiler")` を追加（jvm/native × main/test の全コンパイル分）
2. **`@Language("sql")` と `@JvmField` は Native コンパイルで Unresolved reference**。
   common メタデータコンパイルと jvmTest は通るので、実際に Native タスクを回さないと検出不能
3. **sqlx4k の JVM アーティファクトは Java 21+ 必須**（class file 65.0）→ `kmp-poc` は
   `jvmToolchain(21)`。リポジトリ全体は 17 のまま
4. sqlx4k-codegen を使う場合のみ「**Gradle デーモン自体が 21+**」も必要（KSP2 ワーカーは
   デーモン JVM で動く）。自前プロセッサ（17 でビルド）に置換して解消済み
5. **リテラル補間（`${1}` 等）はフロントエンドの定数畳み込みで plugin に届く前に SQL 本文へ
   埋まる**（既存仕様、定数なので injection ではない）。テストは必ず変数経由で書く
6. FetchSpec の single/singleOrNull は **mapper 適用前の行数で判定**する（mapper が null を
   返す nullable カラムと「行なし」を混同しない）
7. Kotlin/Native はバッククォートテスト名の **`@` を拒否**（スペースは OK）
8. sqlx4k には **行ストリーミングと generated-keys API がない** → `flow()` /
   `generatedValues()` は sqlx4k バックエンドでは提供不可（FetchSpec サブセット化が前提）
9. IR 合成の inline タイミング: reified ラッパーは JVM/Native とも **IR パスが inline より先**
   に走るのでラッパー呼び出しを直接書き換えられる。保険で `KClass` マーカー + リテラル
   `T::class` 形状も認識する二段構え（`recordTypeOrNull()` 参照）
10. バージョン組: Kotlin 2.4.10（本リポジトリ・sqlx4k とも）+ KSP 2.3.10 + sqlx4k 1.12.0

## 動かし方

```bash
./gradlew :kmp-poc:jvmTest :kmp-poc:macosArm64Test   # 全 PoC テスト
./gradlew :kuery-client-compiler:test \
          :kuery-client-compiler:functional-test:test \
          :kuery-client-compiler:functional-test-auto-trim:test  # 回帰確認
```
Kotlin/Native ツールチェーンは初回 `~/.konan` に自動 DL（数分）。Docker 不要（SQLite ファイル DB、
テストごとに `kmp-poc/build/poc-*.db` を作成）。

## 次にやること（本実装への設計課題）

1. **FetchSpec サブセットの API 形**: sqlx4k 用 `FetchSpec`（single/singleOrNull/list/
   rowsUpdated のみ）を core にどう位置づけるか。driver SPI を切って sqlx4k を最初の実装に
   する案を提示済み（sqlx4k は実質単一メンテナ・~310 stars なので直結を避けたい）
2. **core KMP 化のモジュール分割**: observation → jvmMain 移動（FQN 不変なら Spring 系は
   無影響見込み）、build-logic に KMP プリセット新設（`conventions.kotlin` は `kotlin("jvm")`
   固定）、Gradle plugin の `isApplicable` の JVM 限定解除、ABI dump の KMP 対応
3. **RowMapperSynthesisTransformer の本実装化**: 型対応の拡張（enum / value class /
   kotlinx-datetime / ByteArray / カラム名オーバーライド）、FIR チェッカーで IDE に赤線を出す
   事前診断（現状は messageCollector の ERROR のみ）、`Sqlx4kFetchSpec` FQN のハードコード解消
4. sqlx4k 側の残課題: 裸 null バインドの型情報（`bindNull` はあるが kuery 側は型不明）、
   MySQL/Postgres ドライバでの検証（PoC は SQLite のみ）

## 関連情報

- 詳細な調査経緯・決定事項は auto-memory の `sqlx4k-v2-exploration.md` にもあり（このリポジトリ
  外、`~/.claude/projects/.../memory/`）
- v1.2 以降の新機能は塩漬け方針だったが、この v2 検討はユーザー自身の発案で再開したもの
