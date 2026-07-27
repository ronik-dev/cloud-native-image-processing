package ch.supsi.imageprocessing.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RemoveBackgroundRequest(
    @JsonProperty("source_sk") String sourceSk,
    @JsonProperty("target_sk") String targetSk) {}
