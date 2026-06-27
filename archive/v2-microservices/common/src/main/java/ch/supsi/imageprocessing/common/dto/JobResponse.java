package ch.supsi.imageprocessing.common.dto;


public record JobResponse(
    Long id,
    String type,
    String status,
    String outputName,
    Long imageId
) {}
