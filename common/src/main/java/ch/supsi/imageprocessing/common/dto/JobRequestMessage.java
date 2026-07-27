package ch.supsi.imageprocessing.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import ch.supsi.imageprocessing.common.enums.JobType;

public record JobRequestMessage(
    @JsonProperty("job_id") Long jobId,
    @JsonProperty("job_type") JobType jobType,
    @JsonProperty("source_sk") String sourceStorageKey,
    @JsonProperty("target_sk") String targetStorageKey,
    // Only populated for FORMAT_CONVERSION; null otherwise.
    @JsonProperty("input_format") String inputFormat,
    @JsonProperty("output_format") String outputFormat
) {}
