package ch.supsi.imageprocessing.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConvertFormatRequest(
    @JsonProperty("source_sk") String sourceSk,
    @JsonProperty("input_format") String inputFormat,
    @JsonProperty("target_sk") String targetSk,
    @JsonProperty("output_format") String outputFormat) {}
