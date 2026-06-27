package ch.supsi.imageprocessing.common.dto;

import ch.supsi.imageprocessing.common.enums.JobType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

public record JobRequest(
				@NotNull(message = "Job type cannot be blank")
				JobType type,

				@NotEmpty(message = "Output name cannot be empty")
				String outputName,

				@Pattern( regexp = "^(jpg|png|gif|webp|bmp)$", message = "Target format must be one of: jpg, png, gif, webp, bmp")
				String targetFormat
				) {
		public String getOrDefaultTargetFormat() {
				return (targetFormat!= null && !targetFormat.isBlank()) ? targetFormat: "png";
		}
}
