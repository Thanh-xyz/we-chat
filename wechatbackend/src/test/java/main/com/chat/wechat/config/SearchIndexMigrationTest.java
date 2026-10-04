package main.com.chat.wechat.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SearchIndexMigrationTest {
	@Test
	void searchMigrationRequiresPgTrgmAndDefinesEachIndexWithoutDuplicates() throws IOException {
		String migration = Files.readString(Path.of(
				"src/main/resources/db/migration/V18__require_pg_trgm_search_indexes.sql"));

		assertThat(migration)
				.contains("pg_trgm")
				.contains("raise exception")
				.contains("gin_trgm_ops")
				.contains("idx_users_username_trgm")
				.contains("idx_users_email_trgm")
				.contains("idx_users_display_name_trgm")
				.contains("idx_conversations_name_trgm")
				.contains("idx_messages_content_trgm")
				.doesNotContain("Skipping")
				.doesNotContain("raise notice");
	}
}
