package com.exzbt.business.video;

import com.exzbt.business.s3.RekognitionActions;
import com.exzbt.business.video.mappers.VideoDetailsDTO;
import com.exzbt.business.video.mappers.VideoDetailsRequest;
import com.exzbt.business.video.mappers.VideoSubmissionDTO;
import com.exzbt.business.video.mappers.VideoSubmissionRequest;
import com.exzbt.s3.S3Buckets;
import com.exzbt.s3.transactions.S3Actions;
import com.exzbt.transaction.video.api.SubmissionActions;
import com.exzbt.transaction.video.api.VideoActions;
import com.exzbt.transaction.video.impl.Video;
import com.exzbt.transaction.video.impl.VideoSubmission;
import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@AllArgsConstructor
public class VideoService {
    @Autowired
    private VideoActions videoActions;
    @Autowired
    private S3Actions s3Actions;
    @Autowired
    private SubmissionActions submissionActions;
    @Autowired
    private RekognitionActions rekognitionActions;

    private S3Buckets s3Buckets;

    public List<VideoDetailsDTO> getVideosByCreatorId(String creatorId) {
        List<Video> userVideos = videoActions.findByCreatorId(creatorId);
        return userVideos.stream()
                .map(userVideo -> new VideoDetailsDTO().convertDTO(userVideo)).toList();
    }

    public VideoDetailsDTO getVideoByVideoId(String videoId) {
        Video video = videoActions.findByVideoId(videoId)
                .orElseThrow(RuntimeException::new);
        return new VideoDetailsDTO().convertDTO(video);
    }

    public void createVideo(VideoDetailsRequest videoDetailsRequest) {
        videoActions.save(videoDetailsRequest.convertFromDTO());
    }

    public void deleteVideoById(String videoId) {
    }

    public String saveCreatedVideo(String creatorId, String videoName, MultipartFile file, Date created) {
        String videoId = UUID.randomUUID().toString();

        try {
            s3Actions.putObject(
                    s3Buckets.getAppUser(),
                    "createdVideos/%s/%s".formatted(creatorId, videoId),
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new RuntimeException("failed to save video", e);
        }

        VideoDetailsRequest videoRequest = new VideoDetailsRequest();
        videoRequest.setVideoId(videoId);
        videoRequest.setVideoName(videoName);
        videoRequest.setCreatorId(creatorId);
        videoRequest.setCreated(created);
        videoRequest.setSaved(Boolean.TRUE);
        createVideo(videoRequest);
        return videoId;
    }

    public void submitVideo(String videoId, String requestId, String creatorId, Date submitted) {
        VideoSubmissionRequest videoSubmissionRequest = new VideoSubmissionRequest();
        videoSubmissionRequest.setVideoId(videoId);
        videoSubmissionRequest.setRequestId(requestId);
        videoSubmissionRequest.setCreatorId(creatorId);
        videoSubmissionRequest.setSubmitted(submitted);

        submissionActions.save(videoSubmissionRequest.convertFromDTO());
    }

    public byte[] getRequestVideoById(String requestId, String videoId) {
        Video video = videoActions.findById(videoId)
                .orElseThrow(RuntimeException::new);

        if (Objects.isNull(video)) {
            //TODO: exception throw
            return null;
        }

        return s3Actions.getObject(
                s3Buckets.getAppUser(),
                "requestVideos/%s/%s".formatted(requestId, videoId)
        );
    }

    public byte[] getSavedVideoById(String videoId) {
        Video video = videoActions.findById(videoId)
                .orElseThrow(RuntimeException::new);

        if (Objects.isNull(video.getCreatorId())) {
            //TODO: exception throw
            return null;
        }

        return s3Actions.getObject(
                s3Buckets.getAppUser(),
                "createdVideos/%s/%s".formatted(video.getCreatorId(), videoId)
        );
    }

    public List<String> blurCreatedVideo(String creatorId, MultipartFile file) {
        String videoKey = "blurVideos/%s".formatted(creatorId);
        try {
            s3Actions.putObject(
                    s3Buckets.getBlurS3(),
                    videoKey,
                    file.getBytes()
            );
        } catch (IOException e) {
            throw new RuntimeException("failed to upload video to S3", e);
        }

        String startJobId = rekognitionActions.startFaceDetection(s3Buckets.getBlurS3(), videoKey);
        return rekognitionActions.getFaceResults(startJobId);
    }

    public VideoDetailsDTO getVideoByVideoName(String videoName) {
        Video video = videoActions.findByVideoName(videoName)
                .orElseThrow(RuntimeException::new);
        return new VideoDetailsDTO().convertDTO(video);
    }

    public List<VideoSubmissionDTO> getVideoSubmissionsByRequestId(String requestId) {
        List<VideoSubmission> submissions = submissionActions.findByRequestId(requestId);
        return submissions.stream()
                .map(submission -> new VideoSubmissionDTO().convertDTO(submission)).toList();
    }
}
