package ch.supsi.imageprocessing.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record JobResultMessage(
    @JsonProperty("job_id") Long jobId,
    @JsonProperty("status") String status,
    @JsonProperty("target_sk") String targetStorageKey,
    @JsonProperty("error_message") String errorMessage
) {}
