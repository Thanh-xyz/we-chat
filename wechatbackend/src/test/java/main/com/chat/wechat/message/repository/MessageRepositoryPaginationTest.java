package main.com.chat.wechat.message.repository;

import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.pagination.MessageCursor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRepositoryPaginationTest {
	private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

	private JdbcTemplate jdbcTemplate;
	private MessageRepository repository;

	@BeforeEach
	void setUp() {
		String databaseName = "message_pagination_" + UUID.randomUUID().toString().replace("-", "");
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
				"sa",
				"");
		jdbcTemplate = new JdbcTemplate(dataSource);
		repository = new MessageRepository(jdbcTemplate);
		jdbcTemplate.execute("""
				create table users (
					id uuid primary key,
					username varchar(100) not null,
					email varchar(255) not null,
					display_name varchar(255) not null,
					deleted_at timestamp with time zone
				)
				""");
		jdbcTemplate.execute("""
				create table messages (
					id uuid primary key,
					conversation_id uuid not null,
					sender_id uuid not null,
					content text,
					message_type varchar(30) not null,
					reply_to_message_id uuid,
					edited_at timestamp with time zone,
					deleted_at timestamp with time zone,
					recalled_at timestamp with time zone,
					is_edited boolean not null,
					is_recalled boolean not null,
					created_at timestamp with time zone not null,
					updated_at timestamp with time zone not null
				)
				""");
		jdbcTemplate.execute("""
				create table message_user_deletions (
					message_id uuid not null,
					user_id uuid not null,
					deleted_at timestamp with time zone not null,
					primary key (message_id, user_id)
				)
				""");
		insertUser(ACTOR_ID, "actor", "actor@example.com", "Actor");
		insertUser(OTHER_USER_ID, "alice", "alice@example.com", "Alice");
	}

	@Test
	void keysetPageIsStableWhenNewerMessagesAreInsertedBetweenRequests() {
		Instant base = Instant.parse("2026-01-01T00:00:00Z");
		UUID oldestId = id(1);
		UUID secondId = id(2);
		UUID thirdId = id(3);
		UUID newestId = id(4);
		insertMessage(oldestId, ACTOR_ID, "oldest", base.plusSeconds(1));
		insertMessage(secondId, ACTOR_ID, "second", base.plusSeconds(2));
		insertMessage(thirdId, ACTOR_ID, "third", base.plusSeconds(3));
		insertMessage(newestId, ACTOR_ID, "newest", base.plusSeconds(4));

		List<Message> firstPageWithLookahead = repository.findByConversationId(
				CONVERSATION_ID, ACTOR_ID, null, 3);
		MessageCursor cursor = new MessageCursor(
				firstPageWithLookahead.get(1).createdAt(), firstPageWithLookahead.get(1).id());
		// The new row is inserted after the first request and must not move the boundary.
		insertMessage(id(6), ACTOR_ID, "newer inserted", base.plusSeconds(5));

		List<Message> secondPage = repository.findByConversationId(
				CONVERSATION_ID, ACTOR_ID, cursor, 3);

		assertThat(firstPageWithLookahead).extracting(Message::id)
				.containsExactly(newestId, thirdId, secondId);
		assertThat(secondPage).extracting(Message::id).containsExactly(secondId, oldestId);
	}

	@Test
	void idBreaksTiesWhenCreatedAtIsTheSame() {
		Instant createdAt = Instant.parse("2026-02-01T00:00:00Z");
		insertMessage(id(1), ACTOR_ID, "one", createdAt);
		insertMessage(id(2), ACTOR_ID, "two", createdAt);
		insertMessage(id(3), ACTOR_ID, "three", createdAt);

		List<Message> firstPageWithLookahead = repository.findByConversationId(
				CONVERSATION_ID, ACTOR_ID, null, 3);
		MessageCursor cursor = new MessageCursor(
				firstPageWithLookahead.get(1).createdAt(), firstPageWithLookahead.get(1).id());
		List<Message> secondPage = repository.findByConversationId(
				CONVERSATION_ID, ACTOR_ID, cursor, 3);

		assertThat(firstPageWithLookahead).extracting(Message::id)
				.containsExactly(id(3), id(2), id(1));
		assertThat(secondPage).extracting(Message::id).containsExactly(id(1));
	}

	@Test
	void historyAndSearchRetainTheirExistingVisibilityFilters() {
		Instant createdAt = Instant.parse("2026-03-01T00:00:00Z");
		UUID visibleId = id(1);
		UUID recalledId = id(2);
		UUID deletedId = id(3);
		UUID deletedForUserId = id(4);
		insertMessage(visibleId, OTHER_USER_ID, "alice hello visible", createdAt.plusSeconds(4));
		insertMessage(recalledId, OTHER_USER_ID, "hello recalled", createdAt.plusSeconds(3), null, createdAt.plusSeconds(3), true);
		insertMessage(deletedId, OTHER_USER_ID, "hello deleted", createdAt.plusSeconds(2), createdAt.plusSeconds(2), null, false);
		insertMessage(deletedForUserId, OTHER_USER_ID, "hello personal delete", createdAt.plusSeconds(1));
		jdbcTemplate.update(
				"insert into message_user_deletions(message_id, user_id, deleted_at) values (?, ?, ?)",
				deletedForUserId, ACTOR_ID, Timestamp.from(createdAt));

		List<Message> history = repository.findByConversationId(CONVERSATION_ID, ACTOR_ID, null, 10);
		List<Message> search = repository.search(CONVERSATION_ID, ACTOR_ID, "hello", null, 10);
		List<Message> senderSearch = repository.search(CONVERSATION_ID, ACTOR_ID, "alice", null, 10);
		List<Message> injectionSearch = repository.search(CONVERSATION_ID, ACTOR_ID, "' OR 1=1 --", null, 10);

		assertThat(history).extracting(Message::id).containsExactly(visibleId, recalledId);
		assertThat(search).extracting(Message::id).containsExactly(visibleId);
		assertThat(senderSearch).extracting(Message::id).containsExactly(visibleId);
		assertThat(injectionSearch).isEmpty();
	}

	@Test
	void historyNeverReturnsMessagesFromAnotherConversation() {
		UUID otherConversationId = UUID.fromString("00000000-0000-0000-0000-000000000011");
		Instant createdAt = Instant.parse("2026-04-01T00:00:00Z");
		insertMessage(id(1), ACTOR_ID, "in conversation", createdAt, CONVERSATION_ID);
		insertMessage(id(2), ACTOR_ID, "in another conversation", createdAt.plusSeconds(1), otherConversationId);

		List<Message> messages = repository.findByConversationId(CONVERSATION_ID, ACTOR_ID, null, 10);

		assertThat(messages).extracting(Message::id).containsExactly(id(1));
	}

	@Test
	void searchUsesTheSameDeterministicKeysetBoundaryAsHistory() {
		Instant base = Instant.parse("2026-05-01T00:00:00Z");
		insertMessage(id(1), ACTOR_ID, "target oldest", base.plusSeconds(1));
		insertMessage(id(2), ACTOR_ID, "target middle", base.plusSeconds(2));
		insertMessage(id(3), ACTOR_ID, "target newest", base.plusSeconds(3));

		List<Message> firstPage = repository.search(CONVERSATION_ID, ACTOR_ID, "target", null, 2);
		MessageCursor cursor = new MessageCursor(firstPage.get(1).createdAt(), firstPage.get(1).id());
		List<Message> secondPage = repository.search(CONVERSATION_ID, ACTOR_ID, "target", cursor, 2);

		assertThat(firstPage).extracting(Message::id).containsExactly(id(3), id(2));
		assertThat(secondPage).extracting(Message::id).containsExactly(id(1));
	}

	private void insertUser(UUID id, String username, String email, String displayName) {
		jdbcTemplate.update(
				"insert into users(id, username, email, display_name) values (?, ?, ?, ?)",
				id, username, email, displayName);
	}

	private void insertMessage(UUID id, UUID senderId, String content, Instant createdAt) {
		insertMessage(id, senderId, content, createdAt, null, null, false, CONVERSATION_ID);
	}

	private void insertMessage(UUID id, UUID senderId, String content, Instant createdAt, UUID conversationId) {
		insertMessage(id, senderId, content, createdAt, null, null, false, conversationId);
	}

	private void insertMessage(
			UUID id,
			UUID senderId,
			String content,
			Instant createdAt,
			Instant deletedAt,
			Instant recalledAt,
			boolean recalled) {
		insertMessage(id, senderId, content, createdAt, deletedAt, recalledAt, recalled, CONVERSATION_ID);
	}

	private void insertMessage(
			UUID id,
			UUID senderId,
			String content,
			Instant createdAt,
			Instant deletedAt,
			Instant recalledAt,
			boolean recalled,
			UUID conversationId) {
		jdbcTemplate.update("""
				insert into messages(
					id, conversation_id, sender_id, content, message_type, reply_to_message_id,
					edited_at, deleted_at, recalled_at, is_edited, is_recalled, created_at, updated_at)
				values (?, ?, ?, ?, 'TEXT', null, null, ?, ?, false, ?, ?, ?)
				""",
				id,
				conversationId,
				senderId,
				content,
				toTimestamp(deletedAt),
				toTimestamp(recalledAt),
				recalled,
				toTimestamp(createdAt),
				toTimestamp(createdAt));
	}

	private Timestamp toTimestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private UUID id(int suffix) {
		return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
	}
}
