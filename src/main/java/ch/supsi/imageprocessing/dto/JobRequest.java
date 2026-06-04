package ch.supsi.imageprocessing.dto;
import ch.supsi.imageprocessing.entity.JobType;
import jakarta.validation.constraints.NotNull;

public record JobRequest(
    @NotNull(message = "Job type cannot be blank")
    JobType type,

    String outputName,

    String targetFormat
) {
    public String getOrDefaultOutputName() {
        return (outputName != null && !outputName.isBlank()) ? outputName : "processed_output";
    }

    public String getOrDefaultTargetFormat() {
        return (targetFormat!= null && !targetFormat.isBlank()) ? targetFormat: "png";
    }
}
