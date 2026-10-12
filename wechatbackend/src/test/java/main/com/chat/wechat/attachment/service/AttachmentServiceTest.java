package main.com.chat.wechat.attachment.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import main.com.chat.wechat.attachment.dto.AttachmentMetadataRequest;
import main.com.chat.wechat.attachment.storage.FileStorageService;
import main.com.chat.wechat.attachment.storage.StorageProperties;
import main.com.chat.wechat.audit.service.AuditJsonWriter;
import main.com.chat.wechat.audit.service.AuditLogService;
import main.com.chat.wechat.common.exception.ApiException;
import main.com.chat.wechat.conversation.service.ConversationService;
import main.com.chat.wechat.message.model.MessageAttachment;
import main.com.chat.wechat.message.repository.MessageAttachmentRepository;
import main.com.chat.wechat.realtime.service.RealtimeEventPublisher;
import main.com.chat.wechat.user.model.User;
import main.com.chat.wechat.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {
	private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ATTACHMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
	private static final UUID CONVERSATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

	@Mock
	private ConversationService conversationService;

	@Mock
	private MessageAttachmentRepository messageAttachmentRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FileStorageService fileStorageService;

	@Mock
	private AuditLogService auditLogService;

	@Mock
	private AuditJsonWriter auditJsonWriter;

	@Mock
	private RealtimeEventPublisher realtimeEventPublisher;

	private AttachmentService attachmentService;

	@BeforeEach
	void setUp() {
		attachmentService = new AttachmentService(
				conversationService,
				messageAttachmentRepository,
				userRepository,
				fileStorageService,
				new AttachmentProperties(null, null, null, null, null, null),
				auditLogService,
				auditJsonWriter,
			realtimeEventPublisher,
				new SimpleMeterRegistry(),
				new StorageProperties("s3", "uploads"));
	}

	@Test
	void malformedImageIsRejectedAsUnsupportedContentNotStorageOutage() throws Exception {
		byte[] malformedImage = new byte[] {
				(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
				'm', 'a', 'l', 'f', 'o', 'r', 'm', 'e', 'd'
		};
		Instant now = Instant.parse("2026-01-01T00:00:00Z");
		MessageAttachment pending = new MessageAttachment(
				ATTACHMENT_ID,
				null,
				USER_ID,
				CONVERSATION_ID,
				"malformed.png",
				"attachments/quarantine/2026/01/01/" + ATTACHMENT_ID,
				"/api/attachments/" + ATTACHMENT_ID + "/download",
				"image/png",
				"IMAGE",
				(long) malformedImage.length,
				null,
				"PENDING",
				null,
				now,
				now,
				"INIT");
		User user = new User(
				USER_ID,
				"r16-test",
				"r16-test@example.invalid",
				"hash",
				"R16 Test",
				null,
				"OFFLINE",
				"USER",
				true,
				"ACTIVE",
				true,
				0,
				null,
				0,
				null,
				null,
				now,
				now);
		when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
		when(messageAttachmentRepository.findPendingUpload(ATTACHMENT_ID, USER_ID)).thenReturn(Optional.of(pending));
		when(fileStorageService.head(pending.storageKey()))
				.thenReturn(new FileStorageService.StoredObject(pending.storageKey(), malformedImage.length, "image/png"));
		when(fileStorageService.openStream(pending.storageKey()))
				.thenAnswer(invocation -> new ByteArrayInputStream(malformedImage));

		AttachmentMetadataRequest request = new AttachmentMetadataRequest(
				ATTACHMENT_ID,
				"malformed.png",
				"image/png",
				"IMAGE",
				(long) malformedImage.length,
				null);

		assertThatThrownBy(() -> attachmentService.completePresignedUpload(USER_ID, request))
				.isInstanceOfSatisfying(ApiException.class, exception ->
						org.assertj.core.api.Assertions.assertThat(exception.status())
								.isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE));
		verify(messageAttachmentRepository).markStorageFailed(eq(ATTACHMENT_ID), any());
		verify(fileStorageService).delete(pending.storageKey());
		verify(messageAttachmentRepository, never()).finalizeReady(any(), any(), any(), any(), any());
	}
}
