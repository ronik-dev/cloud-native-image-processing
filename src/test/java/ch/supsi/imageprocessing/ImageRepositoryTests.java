package ch.supsi.imageprocessing;

import ch.supsi.imageprocessing.entity.User;
import ch.supsi.imageprocessing.repository.UserRepository;
import ch.supsi.imageprocessing.entity.Image;
import ch.supsi.imageprocessing.repository.ImageRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class ImageRepositoryTests{

    @Autowired
    private ImageRepository imageRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPersistAndRetrieveImage() {
        User user = new User("username", "example@mail.example");
        userRepository.save(user);

		Image image = new Image("imageName", "./example/path/", "png", user);
        Image saved = imageRepository.save(image);

        assertThat(saved.getId()).isNotNull();

        Optional<Image> found = imageRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("imageName");
    }

    @Test
    void shouldEnforceUniqueImageName() {
        User user = new User("username", "example@mail.example");
        userRepository.save(user);

        imageRepository.save(new Image("imageName", "./example/path/", "png", user));

        assertThatThrownBy(() ->
            imageRepository.saveAndFlush(new Image("imageName", "./example/path/", "png", user))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
