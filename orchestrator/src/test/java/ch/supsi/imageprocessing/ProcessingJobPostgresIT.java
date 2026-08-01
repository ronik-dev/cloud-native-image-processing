package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.common.dto.JobRequestMessage;
import ch.supsi.imageprocessing.common.enums.JobType;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;
import ch.supsi.imageprocessing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
    "spring.kafka.bootstrap-servers=localhost:9092",
    "spring.kafka.listener.auto-startup=false",
    "kafka.topics.user-events=user.events",
    "kafka.topics.job-requests=job.requests",
    "kafka.topics.job-results=job.results"
})
class ProcessingJobPostgresIT {

		@Container
		static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18-alpine");

		@DynamicPropertySource
		static void datasourceProperties(DynamicPropertyRegistry registry) {
				registry.add("spring.datasource.url", postgres::getJdbcUrl);
				registry.add("spring.datasource.username", postgres::getUsername);
				registry.add("spring.datasource.password", postgres::getPassword);
				registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
				registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");  // Force Hibernate to use the PostgreSQL dialect instead of H2
		}

		@MockitoBean
		private KafkaTemplate<String, JobRequestMessage> jobRequestKafkaTemplate;

		@Autowired
		private UserRepository userRepository;

		@Autowired
		private ImageRepository imageRepository;

		@Autowired
		private ProcessingJobRepository processingJobRepository;

		@Test
		void deletingUserCascadesToImagesAndJobs() {
				User user = new User("it-user", "it-user@example.com");
				userRepository.save(user);

				Image image = new Image("photo.png", "storage-key-1", "png", user);
				imageRepository.save(image);

				ProcessingJob job = new ProcessingJob(image, JobType.FORMAT_CONVERSION, "output-name", "jpg");
				processingJobRepository.save(job);

				Long imageId = image.getId();
				Long jobId = job.getId();

				userRepository.delete(user);
				userRepository.flush();

				assertThat(imageRepository.findById(imageId)).isEmpty();
				assertThat(processingJobRepository.findById(jobId)).isEmpty();
		}
}
