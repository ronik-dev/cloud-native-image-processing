package ch.supsi.imageprocessing.common.dto;

public record ConvertFormatRequest(
    String sourceSk, String inputFormat, String targetSk, String outputFormat) {}
