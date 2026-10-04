package main.com.chat.wechat.message.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MessageRepositoryUnreadCountTest {
	private static final UUID USER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID USER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID CONVERSATION_A = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID CONVERSATION_B = UUID.fromString("00000000-0000-0000-0000-000000000011");
	private static final UUID CONVERSATION_C = UUID.fromString("00000000-0000-0000-0000-000000000012");
	private static final UUID CONVERSATION_D = UUID.fromString("00000000-0000-0000-0000-000000000013");

	private JdbcTemplate setupJdbcTemplate;
	private JdbcTemplate countingJdbcTemplate;
	private MessageRepository repository;

	@BeforeEach
	void setUp() {
		String databaseName = "message_unread_" + UUID.randomUUID().toString().replace("-", "");
		DriverManagerDataSource dataSource = new DriverManagerDataSource(
				"jdbc:h2:mem:" + databaseName + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
				"sa",
				"");
		setupJdbcTemplate = new JdbcTemplate(dataSource);
		createSchema();
		countingJdbcTemplate = spy(setupJdbcTemplate);
		repository = new MessageRepository(countingJdbcTemplate);
	}

	@Test
	void batchPreservesVisibilityReadMarkersAndUserIsolation() {
		Instant base = Instant.parse("2026-05-01T00:00:00Z");
		insertConversation(CONVERSATION_A);
		insertConversation(CONVERSATION_B);
		insertConversation(CONVERSATION_C);
		insertConversation(CONVERSATION_D);
		insertMember(CONVERSATION_A, USER_A, base);
		insertMember(CONVERSATION_B, USER_A, base.plusSeconds(100));
		insertMember(CONVERSATION_C, USER_A, id(9), null);
		insertMember(CONVERSATION_D, USER_B, base);

		insertMessage(id(1), CONVERSATION_A, USER_B, base.plusSeconds(1));
		insertMessage(id(2), CONVERSATION_A, USER_A, base.plusSeconds(2));
		insertMessage(id(3), CONVERSATION_A, USER_B, base.minusSeconds(1));
		insertMessage(id(4), CONVERSATION_A, USER_B, base.plusSeconds(3), null, base.plusSeconds(3), true);
		insertMessage(id(5), CONVERSATION_A, USER_B, base.plusSeconds(4), base.plusSeconds(4), null, false);
		insertMessage(id(6), CONVERSATION_A, USER_B, base.plusSeconds(5));
		insertMessage(id(7), CONVERSATION_B, USER_B, base.plusSeconds(1));
		insertMessage(id(8), CONVERSATION_D, USER_A, base.plusSeconds(1));
		insertMessage(id(9), CONVERSATION_C, USER_B, base.plusSeconds(1));
		insertMessage(id(10), CONVERSATION_C, USER_B, base.plusSeconds(2));
		markDeletedForUser(id(6), USER_A, base.plusSeconds(6));

		List<UUID> requestedConversationIds = List.of(
				CONVERSATION_A, CONVERSATION_B, CONVERSATION_C, CONVERSATION_D);
		Map<UUID, Integer> userACounts = repository.countUnreadByConversationIds(USER_A, requestedConversationIds);
		Map<UUID, Integer> userBCounts = repository.countUnreadByConversationIds(
				USER_B, List.of(CONVERSATION_A, CONVERSATION_D));

		assertThat(userACounts)
				.containsEntry(CONVERSATION_A, 1)
				.containsEntry(CONVERSATION_B, 0)
				.containsEntry(CONVERSATION_C, 1)
				.doesNotContainKey(CONVERSATION_D);
		assertThat(userBCounts)
				.containsEntry(CONVERSATION_D, 1)
				.doesNotContainKey(CONVERSATION_A);
	}

	@Test
	void tenConversationIdsUseOneAggregationQuery() {
		Instant base = Instant.parse("2026-05-02T00:00:00Z");
		List<UUID> conversationIds = new ArrayList<>();
		for (int index = 0; index < 10; index++) {
			UUID conversationId = id(100 + index);
			conversationIds.add(conversationId);
			insertConversation(conversationId);
			insertMember(conversationId, USER_A, base);
			if (index < 5) {
				insertMessage(id(200 + index), conversationId, USER_B, base.plusSeconds(1));
			}
		}

		Map<UUID, Integer> counts = repository.countUnreadByConversationIds(USER_A, conversationIds);

		for (int index = 0; index < 10; index++) {
			assertThat(counts.get(id(100 + index))).isEqualTo(index < 5 ? 1 : 0);
		}
		ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
		verify(countingJdbcTemplate, times(1)).query(
				sqlCaptor.capture(), any(RowCallbackHandler.class), any(Object[].class));
		assertThat(sqlCaptor.getValue()).contains("group by c.id");
	}

	private void createSchema() {
		setupJdbcTemplate.execute("""
				create table conversations (
					id uuid primary key,
					deleted_at timestamp with time zone
				)
				""");
		setupJdbcTemplate.execute("""
				create table conversation_members (
					conversation_id uuid not null,
					user_id uuid not null,
					last_read_message_id uuid,
					last_read_at timestamp with time zone,
					left_at timestamp with time zone
				)
				""");
		setupJdbcTemplate.execute("""
				create table messages (
					id uuid primary key,
					conversation_id uuid not null,
					sender_id uuid not null,
					created_at timestamp with time zone not null,
					deleted_at timestamp with time zone,
					is_recalled boolean not null,
					recalled_at timestamp with time zone
				)
				""");
		setupJdbcTemplate.execute("""
				create table message_user_deletions (
					message_id uuid not null,
					user_id uuid not null,
					deleted_at timestamp with time zone not null,
					primary key (message_id, user_id)
				)
				""");
	}

	private void insertConversation(UUID conversationId) {
		setupJdbcTemplate.update(
				"insert into conversations(id, deleted_at) values (?, null)", conversationId);
	}

	private void insertMember(UUID conversationId, UUID userId, Instant lastReadAt) {
		insertMember(conversationId, userId, null, lastReadAt);
	}

	private void insertMember(UUID conversationId, UUID userId, UUID lastReadMessageId, Instant lastReadAt) {
		setupJdbcTemplate.update(
				"insert into conversation_members(conversation_id, user_id, last_read_message_id, last_read_at, left_at) values (?, ?, ?, ?, null)",
				conversationId, userId, lastReadMessageId, toTimestamp(lastReadAt));
	}

	private void insertMessage(UUID id, UUID conversationId, UUID senderId, Instant createdAt) {
		insertMessage(id, conversationId, senderId, createdAt, null, null, false);
	}

	private void insertMessage(
			UUID id,
			UUID conversationId,
			UUID senderId,
			Instant createdAt,
			Instant deletedAt,
			Instant recalledAt,
			boolean recalled) {
		setupJdbcTemplate.update("""
				insert into messages(id, conversation_id, sender_id, created_at, deleted_at, is_recalled, recalled_at)
				values (?, ?, ?, ?, ?, ?, ?)
				""",
				id,
				conversationId,
				senderId,
				toTimestamp(createdAt),
				toTimestamp(deletedAt),
				recalled,
				toTimestamp(recalledAt));
	}

	private void markDeletedForUser(UUID messageId, UUID userId, Instant deletedAt) {
		setupJdbcTemplate.update(
				"insert into message_user_deletions(message_id, user_id, deleted_at) values (?, ?, ?)",
				messageId, userId, toTimestamp(deletedAt));
	}

	private Timestamp toTimestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private UUID id(int suffix) {
		return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
	}
}
