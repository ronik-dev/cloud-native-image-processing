package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;   

public interface UserRepository extends JpaRepository<User, Long> {
		Optional<User> findByUsername(String username);
}
