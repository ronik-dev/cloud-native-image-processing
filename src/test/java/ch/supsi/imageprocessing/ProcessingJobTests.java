package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.repository.ImageRepository;
import ch.supsi.imageprocessing.entity.ProcessingJob;
import ch.supsi.imageprocessing.entity.JobType;
import ch.supsi.imageprocessing.repository.ProcessingJobRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ProcessingJobTests{

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProcessingJobRepository processingJobRepository;

    @Test
    void shouldPersistAndRetrieveProcessingJob() {
        User user = new User("username", "example@mail.example");
        userRepository.save(user);

		Image image = new Image("imageName", new byte[0], "png", user);
        imageRepository.save(image);

		ProcessingJob pj = new ProcessingJob(image, JobType.FORMAT_CONVERSION, "outputName", "targetFormat");
		ProcessingJob saved =processingJobRepository.save(pj);

        assertThat(saved.getId()).isNotNull();

        Optional<ProcessingJob> found = processingJobRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getOutputName()).isEqualTo("outputName");
    }

    @Test
    void shouldEnforceUniqueOutputName() {
        User user = new User("username", "example@mail.example");
        userRepository.save(user);
		Image image = new Image("imageName", new byte[0], "png", user); 
        imageRepository.save(image);

		ProcessingJob pj = new ProcessingJob(image, JobType.FORMAT_CONVERSION, "outputName", "targetFormat");
		processingJobRepository.save(pj);

        assertThatThrownBy(() ->
            processingJobRepository.saveAndFlush(new ProcessingJob(image, JobType.FORMAT_CONVERSION, "outputName", "targetFormat"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
