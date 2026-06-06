package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.User;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPersistAndRetrieveUser() {
        User user = new User("username", "username@domain.state");
        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getUsername()).isEqualTo("username");
    }

    @Test
    void shouldEnforceUniqueUsername() {
        userRepository.save(new User("username", "username@domain.state"));

        assertThatThrownBy(() ->
            userRepository.saveAndFlush(new User("username", "username@domain.state"))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
