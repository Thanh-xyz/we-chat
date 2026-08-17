package main.com.chat.wechat.media.dto;

import main.com.chat.wechat.media.model.MediaStatistics;

public record MediaStatisticsResponse(
		long totalFiles,
		long totalImages,
		long totalVideos,
		long totalVoices,
		long totalStorageBytes,
		MediaResponse largestFile,
		MediaResponse latestUpload) {

	public static MediaStatisticsResponse from(MediaStatistics statistics) {
		return new MediaStatisticsResponse(
				statistics.totalFiles(),
				statistics.totalImages(),
				statistics.totalVideos(),
				statistics.totalVoices(),
				statistics.totalStorageBytes(),
				statistics.largestFile() == null ? null : MediaResponse.from(statistics.largestFile()),
				statistics.latestUpload() == null ? null : MediaResponse.from(statistics.latestUpload()));
	}
}
