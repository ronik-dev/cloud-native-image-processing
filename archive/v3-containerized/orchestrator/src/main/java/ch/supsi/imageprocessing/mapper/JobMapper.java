package ch.supsi.imageprocessing.mapper;

import ch.supsi.imageprocessing.common.dto.JobResponse;
import ch.supsi.imageprocessing.entity.ProcessingJob;

public class JobMapper {

    private JobMapper() {}

    public static JobResponse toResponse(ProcessingJob job) {
        return new JobResponse(
            job.getId(),
            job.getType().name(),
            job.getStatus().name(),
            job.getOutputName(),
            job.getImage() != null ? job.getImage().getId() : null
        );
    }
}
