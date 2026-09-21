package main.com.chat.wechat.database;

import main.com.chat.wechat.audit.model.AuditLog;
import main.com.chat.wechat.audit.model.AuditResult;
import main.com.chat.wechat.audit.repository.AuditLogRepository;
import main.com.chat.wechat.auth.model.EmailVerificationToken;
import main.com.chat.wechat.auth.model.PasswordResetToken;
import main.com.chat.wechat.auth.model.RefreshToken;
import main.com.chat.wechat.auth.repository.EmailVerificationTokenRepository;
import main.com.chat.wechat.auth.repository.PasswordResetTokenRepository;
import main.com.chat.wechat.auth.repository.RefreshTokenRepository;
import main.com.chat.wechat.auth.service.AuthService;
import main.com.chat.wechat.auth.service.RefreshTokenService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.model.Conversation;
import main.com.chat.wechat.conversation.repository.ConversationRepository;
import main.com.chat.wechat.friendship.model.FriendRequest;
import main.com.chat.wechat.friendship.model.FriendRequestStatus;
import main.com.chat.wechat.friendship.repository.FriendRequestRepository;
import main.com.chat.wechat.message.model.Message;
import main.com.chat.wechat.message.pagination.MessageCursor;
import main.com.chat.wechat.message.repository.MessageRepository;
import main.com.chat.wechat.message.service.MessageService;
import main.com.chat.wechat.notification.model.Notification;
import main.com.chat.wechat.notification.model.NotificationDelivery;
import main.com.chat.wechat.notification.repository.NotificationRepository;
import main.com.chat.wechat.role.repository.UserRoleRepository;
import main.com.chat.wechat.support.PostgresIntegrationTest;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PostgresCriticalPathsPostgresIT extends PostgresIntegrationTest {
	private static final Instant NOW = Instant.parse("2026-09-14T12:00:00Z");
	private static final UUID USER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	@Autowired
	private DataSource dataSource;

	@Autowired
	private UserRepository users;

	@Autowired
	private UserRoleRepository userRoles;

	@Autowired
	private ConversationRepository conversations;

	@Autowired
	private FriendRequestRepository friendRequests;

	@Autowired
	private MessageRepository messages;

	@Autowired
	private MessageService messageService;

	@Autowired
	private NotificationRepository notifications;

	@Autowired
	private RefreshTokenRepository refreshTokens;

	@Autowired
	private RefreshTokenService refreshTokenService;

	@Autowired
	private AuthService authService;

	@Autowired
	private PasswordResetTokenRepository passwordResetTokens;

	@Autowired
	private EmailVerificationTokenRepository emailVerificationTokens;

	@Autowired
	private AuditLogRepository auditLogs;

	@Test
	void freshDatabaseRunsEveryMigrationAndCreatesPostgresSpecificSchema() {
		List<String> migratedVersions = jdbc.queryForList("""
				select version
				from flyway_schema_history
				where success = true and version is not null
				order by installed_rank
				""", String.class);
		assertThat(migratedVersions).containsExactly(
				"1", "2", "3", "4", "5", "6", "7", "8", "9",
				"10", "11", "12", "13", "14", "15", "16", "17", "18");

		assertThat(jdbc.queryForObject(
				"select count(*) from pg_extension where extname = 'pg_trgm'", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject(
				"select similarity('wechat', 'wecht') > 0", Boolean.class)).isTrue();
		assertThat(jdbc.queryForObject("""
				select data_type
				from information_schema.columns
				where table_schema = 'public' and table_name = 'audit_logs' and column_name = 'metadata'
				""", String.class)).isEqualTo("jsonb");

		Map<String, String> indexes = indexDefinitions();
		for (String indexName : List.of(
				"idx_users_username_trgm",
				"idx_users_email_trgm",
				"idx_users_display_name_trgm",
				"idx_conversations_name_trgm",
				"idx_messages_content_trgm")) {
			assertThat(indexes).containsKey(indexName);
			assertThat(indexes.get(indexName).toLowerCase())
					.contains("using gin", "gin_trgm_ops", "where");
		}
		assertThat(indexes.get("idx_messages_conversation_created_id").toLowerCase())
				.contains("conversation_id", "created_at desc", "id desc", "where (deleted_at is null)");
		assertThat(indexes.get("ix_refresh_tokens_revoked_at_cleanup").toLowerCase())
				.contains("where (revoked_at is not null)");
		assertThat(indexes.get("idx_notifications_read_at_cleanup").toLowerCase())
				.contains("is_read = true", "deleted_at is null");
		assertThat(indexes).doesNotContainKey("idx_messages_conversation_content");

		List<String> constraints = jdbc.queryForList("""
				select conname
				from pg_constraint
				where connamespace = 'public'::regnamespace
				""", String.class);
		assertThat(constraints).contains(
				"uq_direct_conversations_pair",
				"ck_direct_conversations_users_order",
				"ck_friend_requests_not_self",
				"ck_audit_logs_result");
	}

	@Test
	void authTokensRolesAndCaseInsensitiveIdentityUseProductionConstraints() {
		User user = user(id(1), "CaseUser", "Case.User@example.com", "Case User", true, "ACTIVE", null);
		users.save(user);

		assertThat(users.findByUsernameOrEmail("caseuser")).contains(user);
		assertThat(users.findByUsernameOrEmail("CASE.USER@EXAMPLE.COM")).contains(user);
		assertThatThrownBy(() -> users.save(user(
				id(2), "AnotherUser", "case.user@EXAMPLE.com", "Duplicate", true, "ACTIVE", NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);

		userRoles.assign(user.id(), USER_ROLE_ID, user.id(), NOW);
		assertThat(userRoles.findRoleCodesByUserId(user.id())).containsExactly("USER");
		assertThat(userRoles.findPermissionCodesByUserId(user.id()))
				.contains("CONVERSATION_READ", "MESSAGE_READ", "MESSAGE_SEND", "USER_WRITE");

		RefreshToken refresh = new RefreshToken(
				id(10), user.id(), hash('r'), NOW.plusSeconds(3600), null, NOW,
				null, "integration-device", "192.0.2.10");
		PasswordResetToken reset = new PasswordResetToken(
				id(11), user.id(), hash('p'), NOW.plusSeconds(900), null, NOW);
		EmailVerificationToken verification = new EmailVerificationToken(
				id(12), user.id(), hash('e'), NOW.plusSeconds(3600), null, NOW);
		refreshTokens.save(refresh);
		passwordResetTokens.save(reset);
		emailVerificationTokens.save(verification);

		assertThat(refreshTokens.findByTokenHash(hash('r'))).contains(refresh);
		assertThat(passwordResetTokens.findByTokenHash(hash('p'))).contains(reset);
		assertThat(emailVerificationTokens.findByTokenHash(hash('e'))).contains(verification);
		refreshTokens.revoke(hash('r'), NOW.plusSeconds(1), hash('n'));
		passwordResetTokens.markUsed(hash('p'), NOW.plusSeconds(1));
		emailVerificationTokens.markUsed(hash('e'), NOW.plusSeconds(1));
		assertThat(refreshTokens.findByTokenHash(hash('r')).orElseThrow().revokedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(passwordResetTokens.findByTokenHash(hash('p')).orElseThrow().usedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThat(emailVerificationTokens.findByTokenHash(hash('e')).orElseThrow().usedAt()).isEqualTo(NOW.plusSeconds(1));
		assertThatThrownBy(() -> refreshTokens.save(new RefreshToken(
				id(13), user.id(), hash('r'), NOW.plusSeconds(7200), null, NOW,
				null, null, null)))
				.isInstanceOf(DataIntegrityViolationException.class);

		RefreshTokenService.GeneratedRefreshToken original = refreshTokenService.create(
				user.id(), Instant.now(), null);
		String rotatedRawToken = authService.refresh(original.rawToken(), null).refreshToken();
		RefreshToken rotatedOriginal = refreshTokens.findByTokenHash(original.tokenHash()).orElseThrow();
		assertThat(rotatedOriginal.revokedAt()).isNotNull();
		assertThat(rotatedOriginal.replacedByToken()).isEqualTo(refreshTokenService.hash(rotatedRawToken));
		assertThatThrownBy(() -> authService.refresh(original.rawToken(), null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("Refresh token reuse detected");
	}

	@Test
	void activeUserSearchHonorsPartialIndexPredicatesAndDeletedState() {
		User actor = insertUser(20, "actor", "actor@example.com", "Actor");
		User visible = user(id(21), "visible-alice", "visible@example.com", "Visible Active", true, "ACTIVE", null);
		User disabled = user(id(22), "visible-disabled", "disabled@example.com", "Visible Disabled", false, "ACTIVE", null);
		User blocked = user(id(23), "visible-blocked", "blocked@example.com", "Visible Blocked", true, "BLOCKED", null);
		User deleted = user(id(24), "visible-deleted", "deleted@example.com", "Visible Deleted", true, "ACTIVE", NOW);
		users.save(visible);
		users.save(disabled);
		users.save(blocked);
		users.save(deleted);

		assertThat(users.searchActiveUsers(actor.id(), "visible", 20, 0))
				.extracting(User::id)
				.containsExactly(visible.id());
	}

	@Test
	void directConversationAndPendingFriendRequestRacesAreDatabaseProtected() throws Exception {
		User first = insertUser(30, "first", "first@example.com", "First");
		User second = insertUser(31, "second", "second@example.com", "Second");
		UUID firstConversation = insertConversation(32, "DIRECT", null, first.id(), null);
		UUID secondConversation = insertConversation(33, "DIRECT", null, first.id(), null);

		List<Object> directResults = race(
				() -> conversations.saveDirectConversation(firstConversation, first.id(), second.id()),
				() -> conversations.saveDirectConversation(secondConversation, second.id(), first.id()));
		assertThat(directResults).containsExactlyInAnyOrder(true, false);
		assertThat(jdbc.queryForObject("select count(*) from direct_conversations", Integer.class)).isEqualTo(1);

		FriendRequest forward = friendRequest(id(34), first.id(), second.id());
		FriendRequest reverse = friendRequest(id(35), second.id(), first.id());
		List<Object> friendResults = race(
				() -> saveFriendRequest(forward),
				() -> saveFriendRequest(reverse));
		assertThat(friendResults).filteredOn("inserted"::equals).hasSize(1);
		assertThat(friendResults).filteredOn(DataIntegrityViolationException.class::isInstance).hasSize(1);
		assertThat(jdbc.queryForObject(
				"select count(*) from friend_requests where status = 'PENDING'", Integer.class)).isEqualTo(1);

		jdbc.update("update friend_requests set status = 'CANCELLED', responded_at = ?, updated_at = ?",
				timestamp(NOW), timestamp(NOW));
		friendRequests.save(friendRequest(id(36), second.id(), first.id()));
		assertThat(jdbc.queryForObject(
				"select count(*) from friend_requests where status = 'PENDING'", Integer.class)).isEqualTo(1);
	}

	@Test
	void conversationMembershipAndDeletedRowsRemainIsolated() {
		User actor = insertUser(40, "member", "member@example.com", "Member");
		User owner = insertUser(41, "owner", "owner@example.com", "Owner");
		UUID visible = insertConversation(42, "GROUP", "Visible room", owner.id(), null);
		UUID nonMember = insertConversation(43, "GROUP", "Not joined", owner.id(), null);
		UUID deleted = insertConversation(44, "GROUP", "Deleted room", owner.id(), NOW);
		UUID left = insertConversation(45, "GROUP", "Left room", owner.id(), null);
		insertMember(visible, actor.id(), null, null, null);
		insertMember(deleted, actor.id(), null, null, null);
		insertMember(left, actor.id(), null, null, NOW);

		assertThat(conversations.findByMember(actor.id(), true, 20, 0))
				.extracting(Conversation::id)
				.containsExactly(visible)
				.doesNotContain(nonMember, deleted, left);
	}

	@Test
	void messageKeysetIsStableAcrossTimestampTiesAndNewerInsertions() {
		User actor = insertUser(50, "keyset", "keyset@example.com", "Keyset");
		UUID conversationId = insertConversation(51, "GROUP", "Keyset room", actor.id(), null);
		Instant base = Instant.parse("2026-01-01T00:00:00Z");
		insertMessage(52, conversationId, actor.id(), "oldest", base.plusSeconds(1));
		insertMessage(53, conversationId, actor.id(), "middle", base.plusSeconds(2));
		insertMessage(54, conversationId, actor.id(), "tie-low", base.plusSeconds(3));
		insertMessage(55, conversationId, actor.id(), "tie-high", base.plusSeconds(3));

		List<Message> firstPageWithLookahead = messages.findByConversationId(conversationId, actor.id(), null, 3);
		MessageCursor cursor = new MessageCursor(
				firstPageWithLookahead.get(1).createdAt(), firstPageWithLookahead.get(1).id());
		insertMessage(56, conversationId, actor.id(), "inserted after page one", base.plusSeconds(4));
		List<Message> secondPage = messages.findByConversationId(conversationId, actor.id(), cursor, 3);

		assertThat(firstPageWithLookahead).extracting(Message::id)
				.containsExactly(id(55), id(54), id(53));
		assertThat(secondPage).extracting(Message::id).containsExactly(id(53), id(52));
		assertThat(List.of(firstPageWithLookahead.get(0).id(), firstPageWithLookahead.get(1).id(),
				secondPage.get(0).id(), secondPage.get(1).id()))
				.containsExactly(id(55), id(54), id(53), id(52))
				.doesNotHaveDuplicates();
	}

	@Test
	void messageSearchCoversContentSenderDedupeCursorAndVisibilityOnPostgres() {
		User actor = insertUser(60, "searcher", "searcher@example.com", "Searcher");
		User alice = insertUser(61, "alice", "alice@example.com", "Alice Common");
		User bob = insertUser(62, "bob", "bob@example.com", "Bob");
		UUID conversationId = insertConversation(63, "GROUP", "Search room", actor.id(), null);
		insertMember(conversationId, actor.id(), null, null, null);
		Instant base = Instant.parse("2026-02-01T00:00:00Z");
		insertMessage(64, conversationId, alice.id(), "common term from alice", base.plusSeconds(1));
		insertMessage(65, conversationId, bob.id(), "common term from bob", base.plusSeconds(2));
		insertMessage(66, conversationId, bob.id(), "rare-comet", base.plusSeconds(3));
		insertMessage(67, conversationId, bob.id(), "common recalled", base.plusSeconds(4), null, base.plusSeconds(4), true);
		insertMessage(68, conversationId, bob.id(), "common deleted", base.plusSeconds(5), base.plusSeconds(5), null, false);
		insertMessage(69, conversationId, bob.id(), "common hidden", base.plusSeconds(6));
		jdbc.update("insert into message_user_deletions(message_id, user_id, deleted_at) values (?, ?, ?)",
				id(69), actor.id(), timestamp(base));

		assertThat(messages.search(conversationId, actor.id(), "common", null, 20))
				.extracting(Message::id)
				.containsExactly(id(65), id(64))
				.doesNotHaveDuplicates();
		assertThat(messages.search(conversationId, actor.id(), "alice", null, 20))
				.extracting(Message::id)
				.containsExactly(id(64));
		assertThat(messages.search(conversationId, actor.id(), "rare-comet", null, 20))
				.extracting(Message::id)
				.containsExactly(id(66));
		assertThat(messages.search(conversationId, actor.id(), "no-such-result", null, 20)).isEmpty();
		assertThat(messages.search(conversationId, actor.id(), "' OR 1=1 --", null, 20)).isEmpty();
		assertThat(messages.search(conversationId, actor.id(), "x".repeat(2_000), null, 20)).isEmpty();
		assertThatThrownBy(() -> messageService.search(alice.id(), conversationId, "common", 20, null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("not a member of this conversation");
		assertThatThrownBy(() -> messageService.search(actor.id(), conversationId, "x".repeat(101), 20, null))
				.isInstanceOf(ApiException.class)
				.hasMessageContaining("100 characters or fewer");

		insertMessage(70, conversationId, bob.id(), "page-marker oldest", base.plusSeconds(10));
		insertMessage(71, conversationId, bob.id(), "page-marker middle", base.plusSeconds(11));
		insertMessage(72, conversationId, bob.id(), "page-marker newest", base.plusSeconds(12));
		List<Message> firstPage = messages.search(conversationId, actor.id(), "page-marker", null, 2);
		MessageCursor cursor = new MessageCursor(firstPage.get(1).createdAt(), firstPage.get(1).id());
		assertThat(firstPage).extracting(Message::id).containsExactly(id(72), id(71));
		assertThat(messages.search(conversationId, actor.id(), "page-marker", cursor, 2))
				.extracting(Message::id)
				.containsExactly(id(70));
	}

	@Test
	void batchUnreadPreservesMembershipReadMarkersAndVisibilityInOneQuery() {
		User userA = insertUser(80, "unread-a", "unread-a@example.com", "Unread A");
		User userB = insertUser(81, "unread-b", "unread-b@example.com", "Unread B");
		UUID conversationA = insertConversation(82, "GROUP", "A", userA.id(), null);
		UUID conversationB = insertConversation(83, "GROUP", "B", userA.id(), null);
		UUID conversationC = insertConversation(84, "GROUP", "C", userA.id(), null);
		UUID conversationD = insertConversation(85, "GROUP", "D", userA.id(), null);
		Instant base = Instant.parse("2026-05-01T00:00:00Z");

		insertMessage(86, conversationA, userB.id(), "unread", base.plusSeconds(1));
		insertMessage(87, conversationA, userA.id(), "own", base.plusSeconds(2));
		insertMessage(88, conversationA, userB.id(), "already read", base.minusSeconds(1));
		insertMessage(89, conversationA, userB.id(), "recalled", base.plusSeconds(3), null, base.plusSeconds(3), true);
		insertMessage(90, conversationA, userB.id(), "deleted", base.plusSeconds(4), base.plusSeconds(4), null, false);
		insertMessage(91, conversationA, userB.id(), "hidden", base.plusSeconds(5));
		insertMessage(92, conversationB, userB.id(), "before marker", base.plusSeconds(1));
		insertMessage(93, conversationD, userA.id(), "for user b", base.plusSeconds(1));
		insertMessage(94, conversationC, userB.id(), "marker", base.plusSeconds(1));
		insertMessage(95, conversationC, userB.id(), "after marker", base.plusSeconds(2));
		insertMember(conversationA, userA.id(), null, base, null);
		insertMember(conversationB, userA.id(), null, base.plusSeconds(100), null);
		insertMember(conversationC, userA.id(), id(94), null, null);
		insertMember(conversationD, userB.id(), null, base, null);
		jdbc.update("insert into message_user_deletions(message_id, user_id, deleted_at) values (?, ?, ?)",
				id(91), userA.id(), timestamp(base.plusSeconds(6)));

		JdbcTemplate countingJdbc = spy(new JdbcTemplate(dataSource));
		MessageRepository countedMessages = new MessageRepository(countingJdbc);
		clearInvocations(countingJdbc);
		Map<UUID, Integer> userACounts = countedMessages.countUnreadByConversationIds(
				userA.id(), List.of(conversationA, conversationB, conversationC, conversationD));
		Map<UUID, Integer> userBCounts = countedMessages.countUnreadByConversationIds(
				userB.id(), List.of(conversationA, conversationD));

		assertThat(userACounts)
				.containsEntry(conversationA, 1)
				.containsEntry(conversationB, 0)
				.containsEntry(conversationC, 1)
				.doesNotContainKey(conversationD);
		assertThat(userBCounts)
				.containsEntry(conversationD, 1)
				.doesNotContainKey(conversationA);
		verify(countingJdbc, times(2)).query(
				any(String.class), any(RowCallbackHandler.class), any(Object[].class));
	}

	@Test
	void notificationBatchesPersistDeliveriesAndKeepRecipientsIsolated() {
		User actor = insertUser(100, "notify-actor", "notify-actor@example.com", "Actor");
		User first = insertUser(101, "notify-first", "notify-first@example.com", "First");
		User second = insertUser(102, "notify-second", "notify-second@example.com", "Second");
		User noNotifications = insertUser(103, "notify-none", "notify-none@example.com", "None");
		List<Notification> batch = List.of(
				notification(104, first.id(), actor.id(), false, null, null),
				notification(105, first.id(), actor.id(), true, NOW, null),
				notification(106, second.id(), actor.id(), false, null, null),
				notification(107, second.id(), actor.id(), false, null, NOW));

		notifications.saveAll(batch);
		notifications.saveDeliveries(List.of(
				new NotificationDelivery(id(108), id(104), "IN_APP", "SENT", NOW, NOW),
				new NotificationDelivery(id(109), id(106), "PUSH", "PENDING", null, NOW)));

		assertThat(notifications.findByUserId(first.id(), 20, 0)).extracting(Notification::id)
				.containsExactlyInAnyOrder(id(105), id(104));
		assertThat(notifications.countUnreadByUserIds(List.of(first.id(), second.id(), noNotifications.id())))
				.containsEntry(first.id(), 1)
				.containsEntry(second.id(), 1)
				.containsEntry(noNotifications.id(), 0);
		assertThat(jdbc.queryForObject("select count(*) from notification_delivery", Integer.class)).isEqualTo(2);
		assertThatThrownBy(() -> notifications.saveAll(List.of(
				notification(110, id(999_999), actor.id(), false, null, null))))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void cleanupQueriesApplyCutoffsPartialStatesAndBatchLimitsOnPostgres() {
		User user = insertUser(120, "cleanup", "cleanup@example.com", "Cleanup");
		Instant cutoff = NOW.minusSeconds(30L * 24 * 60 * 60);
		insertRefresh(121, user.id(), cutoff.minusSeconds(1), null);
		insertRefresh(122, user.id(), NOW.plusSeconds(3600), cutoff.minusSeconds(1));
		insertRefresh(123, user.id(), NOW.plusSeconds(3600), null);
		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 1)).isEqualTo(1);
		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 1)).isEqualTo(1);
		assertThat(refreshTokens.deleteExpiredOrRevokedBefore(cutoff, cutoff, 1)).isZero();
		assertThat(jdbc.queryForObject("select count(*) from refresh_tokens", Integer.class)).isEqualTo(1);

		insertReset(124, user.id(), cutoff.minusSeconds(1), null);
		insertReset(125, user.id(), NOW.plusSeconds(3600), cutoff.minusSeconds(1));
		insertReset(126, user.id(), NOW.plusSeconds(3600), null);
		assertThat(passwordResetTokens.deleteExpiredOrUsedBefore(cutoff, cutoff, 10)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from password_reset_tokens", Integer.class)).isEqualTo(1);

		insertVerification(127, user.id(), cutoff.minusSeconds(1), null);
		insertVerification(128, user.id(), NOW.plusSeconds(3600), cutoff.minusSeconds(1));
		insertVerification(129, user.id(), NOW.plusSeconds(3600), null);
		assertThat(emailVerificationTokens.deleteExpiredOrUsedBefore(cutoff, cutoff, 10)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from email_verification_tokens", Integer.class)).isEqualTo(1);

		notifications.saveAll(List.of(
				notification(130, user.id(), null, true, cutoff.minusSeconds(1), null),
				notification(131, user.id(), null, false, null, cutoff.minusSeconds(1)),
				notification(132, user.id(), null, false, null, null),
				notification(133, user.id(), null, true, cutoff.plusSeconds(1), null)));
		assertThat(notifications.deleteReadOrSoftDeletedBefore(cutoff, cutoff, 10)).isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from notifications", Integer.class)).isEqualTo(2);

		Instant standardCutoff = NOW.minusSeconds(180L * 24 * 60 * 60);
		Instant securityCutoff = NOW.minusSeconds(365L * 24 * 60 * 60);
		insertAudit(134, "MESSAGE_EDIT", standardCutoff.minusSeconds(1));
		insertAudit(135, "AUTH_LOGIN_FAILED", standardCutoff.minusSeconds(1));
		insertAudit(136, "AUTH_LOGIN_FAILED", securityCutoff.minusSeconds(1));
		insertAudit(137, "MESSAGE_EDIT", standardCutoff.plusSeconds(1));
		assertThat(auditLogs.deleteBefore(
				standardCutoff, securityCutoff, Set.of("AUTH_LOGIN_FAILED", "SECURITY_ACCESS_DENIED"), 10))
				.isEqualTo(2);
		assertThat(jdbc.queryForObject("select count(*) from audit_logs", Integer.class)).isEqualTo(2);
	}

	@Test
	void auditMetadataUsesJsonbAndRejectsMalformedJson() {
		User actor = insertUser(140, "auditor", "auditor@example.com", "Auditor");
		AuditLog valid = audit(
				141,
				actor.id(),
				"{\"state\":\"old\"}",
				"{\"state\":\"new\"}",
				"{\"source\":\"postgres-it\",\"nested\":{\"ok\":true}}");
		AuditLog nullJson = audit(142, actor.id(), null, null, null);
		auditLogs.save(valid);
		auditLogs.save(nullJson);

		AuditLog stored = auditLogs.findById(valid.id()).orElseThrow();
		assertThat(stored.id()).isEqualTo(valid.id());
		assertThat(stored.action()).isEqualTo(valid.action());
		assertThat(stored.resourceType()).isEqualTo(valid.resourceType());
		assertThat(jdbc.queryForObject(
				"select metadata ->> 'source' from audit_logs where id = ?", String.class, valid.id()))
				.isEqualTo("postgres-it");
		assertThat(jdbc.queryForObject(
				"select (metadata #>> '{nested,ok}')::boolean from audit_logs where id = ?", Boolean.class, valid.id()))
				.isTrue();
		assertThat(jdbc.queryForObject(
				"select metadata is null from audit_logs where id = ?", Boolean.class, nullJson.id())).isTrue();
		assertThatThrownBy(() -> auditLogs.save(audit(143, actor.id(), null, null, "{not-valid-json")))
				.isInstanceOf(DataAccessException.class);
	}

	@Test
	void timestamptzRoundTripsAsUtcInstantIndependentOfClientOffset() {
		User sender = insertUser(150, "timezone", "timezone@example.com", "Timezone");
		UUID conversationId = insertConversation(151, "GROUP", "Timezone", sender.id(), null);
		Instant precise = Instant.parse("2026-06-01T02:03:04.123456Z");
		insertMessage(152, conversationId, sender.id(), "utc", precise);

		assertThat(messages.findById(id(152)).orElseThrow().createdAt()).isEqualTo(precise);
		OffsetDateTime clientOffsetValue = jdbc.queryForObject(
				"select cast(? as timestamptz)",
				(rs, rowNum) -> rs.getObject(1, OffsetDateTime.class),
				"2026-06-01T09:03:04.123456+07:00");
		assertThat(clientOffsetValue).isNotNull();
		assertThat(clientOffsetValue.toInstant()).isEqualTo(precise);
		assertThat(jdbc.queryForObject("select current_setting('TimeZone')", String.class).toUpperCase())
				.isIn("UTC", "ETC/UTC");
	}

	@Test
	@EnabledIfEnvironmentVariable(named = "RUN_DB_PERF_TESTS", matches = "(?i)true|1")
	void explainAnalyzeUsesKeysetAndTrigramIndexes() throws Exception {
		User sender = insertUser(170, "perf", "perf@example.com", "Performance");
		UUID conversationId = insertConversation(171, "GROUP", "Performance", sender.id(), null);
		jdbc.update("""
				insert into messages (
				    id, conversation_id, sender_id, content, message_type,
				    is_edited, is_recalled, created_at, updated_at
				)
				select md5('r18-perf-' || series)::uuid,
				       ?, ?,
				       case when series % 100 = 0 then 'rare-performance-needle' else 'ordinary message ' || series end,
				       'TEXT', false, false,
				       timestamptz '2026-01-01 00:00:00+00' + series * interval '1 millisecond',
				       timestamptz '2026-01-01 00:00:00+00' + series * interval '1 millisecond'
				from generate_series(1, 5000) series
				""", conversationId, sender.id());
		jdbc.execute("analyze messages");

		String historyPlan;
		String searchPlan;
		try (Connection connection = dataSource.getConnection()) {
			connection.setAutoCommit(false);
			try (Statement statement = connection.createStatement()) {
				statement.execute("set local enable_seqscan = off");
			}
			historyPlan = explain(connection, """
					select * from messages
					where conversation_id = ? and deleted_at is null
					  and (created_at < ? or (created_at = ? and id < ?))
					order by created_at desc, id desc
					limit 50
					""", conversationId, timestamp(Instant.parse("2026-01-01T00:00:04Z")),
					timestamp(Instant.parse("2026-01-01T00:00:04Z")), id(999_000));
			searchPlan = explain(connection, """
					select * from messages
					where conversation_id = ?
					  and deleted_at is null and is_recalled = false and recalled_at is null
					  and lower(content) like ?
					order by created_at desc, id desc
					limit 50
					""", conversationId, "%rare-performance-needle%");
			connection.rollback();
		}

		System.out.println("R18 keyset EXPLAIN (ANALYZE, BUFFERS):\n" + historyPlan);
		System.out.println("R18 trigram EXPLAIN (ANALYZE, BUFFERS):\n" + searchPlan);
		assertThat(historyPlan).contains("idx_messages_conversation_created_id");
		assertThat(searchPlan).contains("idx_messages_content_trgm");
		assertThat(historyPlan.toLowerCase()).doesNotContain("offset");
	}

	private Map<String, String> indexDefinitions() {
		Map<String, String> result = new LinkedHashMap<>();
		jdbc.query("select indexname, indexdef from pg_indexes where schemaname = 'public'",
				(RowCallbackHandler) rs -> result.put(rs.getString("indexname"), rs.getString("indexdef")));
		return result;
	}

	private User insertUser(int suffix, String username, String email, String displayName) {
		User user = user(id(suffix), username, email, displayName, true, "ACTIVE", null);
		users.save(user);
		return user;
	}

	private User user(
			UUID id,
			String username,
			String email,
			String displayName,
			boolean enabled,
			String accountStatus,
			Instant deletedAt) {
		return new User(
				id, username, email, "password-hash", displayName, null, "OFFLINE", "USER",
				enabled, accountStatus, true, 0, null, 0, null, deletedAt, NOW, NOW);
	}

	private UUID insertConversation(
			int suffix,
			String type,
			String name,
			UUID createdBy,
			Instant deletedAt) {
		UUID conversationId = id(suffix);
		conversations.save(new Conversation(
				conversationId, type, name, null, createdBy, null, null, deletedAt, NOW, NOW));
		return conversationId;
	}

	private void insertMember(
			UUID conversationId,
			UUID userId,
			UUID lastReadMessageId,
			Instant lastReadAt,
			Instant leftAt) {
		jdbc.update("""
				insert into conversation_members (
				    conversation_id, user_id, member_role, joined_at,
				    last_read_message_id, last_read_at, left_at
				)
				values (?, ?, 'MEMBER', ?, ?, ?, ?)
				""", conversationId, userId, timestamp(NOW), lastReadMessageId,
				timestamp(lastReadAt), timestamp(leftAt));
	}

	private Message insertMessage(int suffix, UUID conversationId, UUID senderId, String content, Instant createdAt) {
		return insertMessage(suffix, conversationId, senderId, content, createdAt, null, null, false);
	}

	private Message insertMessage(
			int suffix,
			UUID conversationId,
			UUID senderId,
			String content,
			Instant createdAt,
			Instant deletedAt,
			Instant recalledAt,
			boolean recalled) {
		Message message = new Message(
				id(suffix), conversationId, senderId, content, "TEXT", null,
				null, deletedAt, recalledAt, false, recalled, createdAt, createdAt);
		messages.save(message);
		return message;
	}

	private FriendRequest friendRequest(UUID requestId, UUID requesterId, UUID receiverId) {
		return new FriendRequest(
				requestId, requesterId, receiverId, FriendRequestStatus.PENDING,
				"hello", null, NOW.plusSeconds(7 * 24 * 60 * 60), NOW, NOW);
	}

	private Object saveFriendRequest(FriendRequest request) {
		try {
			friendRequests.save(request);
			return "inserted";
		} catch (DataIntegrityViolationException exception) {
			return exception;
		}
	}

	private Notification notification(
			int suffix,
			UUID userId,
			UUID actorUserId,
			boolean read,
			Instant readAt,
			Instant deletedAt) {
		return new Notification(
				id(suffix), userId, actorUserId, null, null,
				"SYSTEM", "Integration notification", "body", read, NOW, readAt, deletedAt);
	}

	private void insertRefresh(int suffix, UUID userId, Instant expiresAt, Instant revokedAt) {
		refreshTokens.save(new RefreshToken(
				id(suffix), userId, hashFor(suffix), expiresAt, revokedAt, NOW,
				null, null, null));
	}

	private void insertReset(int suffix, UUID userId, Instant expiresAt, Instant usedAt) {
		passwordResetTokens.save(new PasswordResetToken(
				id(suffix), userId, hashFor(suffix), expiresAt, usedAt, NOW));
	}

	private void insertVerification(int suffix, UUID userId, Instant expiresAt, Instant usedAt) {
		emailVerificationTokens.save(new EmailVerificationToken(
				id(suffix), userId, hashFor(suffix), expiresAt, usedAt, NOW));
	}

	private void insertAudit(int suffix, String action, Instant createdAt) {
		AuditLog log = new AuditLog(
				id(suffix), null, null, null, action, "SYSTEM", "cleanup-" + suffix,
				null, null, null, null, null, null, null, null,
				null, null, AuditResult.SUCCESS, null, createdAt);
		auditLogs.save(log);
	}

	private AuditLog audit(
			int suffix,
			UUID actorUserId,
			String beforeValue,
			String afterValue,
			String metadata) {
		return new AuditLog(
				id(suffix), actorUserId, "auditor", "auditor@example.com",
				"AUTH_LOGIN_SUCCESS", "USER", actorUserId.toString(), actorUserId,
				null, null, "request-" + suffix, "trace-" + suffix,
				beforeValue, afterValue, metadata, "192.0.2.20", "postgres-it",
				AuditResult.SUCCESS, null, NOW);
	}

	private List<Object> race(Callable<?> first, Callable<?> second) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Object>> futures = new ArrayList<>();
		try {
			for (Callable<?> task : List.of(first, second)) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					if (!start.await(10, TimeUnit.SECONDS)) {
						throw new IllegalStateException("Timed out waiting for concurrent database test start");
					}
					return task.call();
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<Object> results = new ArrayList<>();
			for (Future<Object> future : futures) {
				results.add(future.get(20, TimeUnit.SECONDS));
			}
			return results;
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private String explain(Connection connection, String query, Object... arguments) throws Exception {
		try (PreparedStatement statement = connection.prepareStatement("explain (analyze, buffers) " + query)) {
			for (int index = 0; index < arguments.length; index++) {
				statement.setObject(index + 1, arguments[index]);
			}
			try (ResultSet resultSet = statement.executeQuery()) {
				List<String> lines = new ArrayList<>();
				while (resultSet.next()) {
					lines.add(resultSet.getString(1));
				}
				return String.join(System.lineSeparator(), lines);
			}
		}
	}

	private String hash(char character) {
		return String.valueOf(character).repeat(64);
	}

	private String hashFor(int suffix) {
		return "%064d".formatted(suffix);
	}

	private Timestamp timestamp(Instant value) {
		return value == null ? null : Timestamp.from(value);
	}

	private UUID id(int suffix) {
		return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
	}
}
