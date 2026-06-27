package ch.supsi.imageprocessing.repository;

import ch.supsi.imageprocessing.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
