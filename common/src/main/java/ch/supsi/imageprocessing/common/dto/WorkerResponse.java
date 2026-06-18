package ch.supsi.imageprocessing.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WorkerResponse(
    @JsonProperty("target_sk") String targetSk) {}
