package main.com.chat.wechat.media.model;

public record MediaStatistics(
		long totalFiles,
		long totalImages,
		long totalVideos,
		long totalVoices,
		long totalRegularFiles,
		long totalStorageBytes,
		MediaItem largestFile,
		MediaItem latestUpload) {
}
