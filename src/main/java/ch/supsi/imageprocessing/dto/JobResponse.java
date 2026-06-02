package ch.supsi.imageprocessing.dto;

import ch.supsi.imageprocessing.entity.ProcessingJob;

public record JobResponse(
    Long id,
    String type,
    String status,
    String outputName,
    String resultPath,
    Long imageId
) {
    public static JobResponse fromEntity(ProcessingJob job) {
        return new JobResponse(
            job.getId(),
            job.getType().name(), 
            job.getStatus().name(),
            job.getOutputName(),
            job.getResultPath(),
            job.getImage() != null ? job.getImage().getId() : null
        );
    }
}
