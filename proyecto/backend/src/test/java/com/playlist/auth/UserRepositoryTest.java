package com.playlist.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void findByUsername_foundAndEmpty() {
        User saved = userRepository.save(User.builder()
                .username("alice")
                .passwordHash("$2a$12$dummyhash")
                .enabled(true)
                .build());

        Optional<User> found = userRepository.findByUsername("alice");
        Optional<User> empty = userRepository.findByUsername("bob");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
        assertThat(found.get().getUsername()).isEqualTo("alice");
        assertThat(empty).isEmpty();
    }

    @Test
    void existsByUsername() {
        userRepository.save(User.builder()
                .username("exists")
                .passwordHash("$2a$12$dummy")
                .enabled(true)
                .build());

        assertThat(userRepository.existsByUsername("exists")).isTrue();
        assertThat(userRepository.existsByUsername("missing")).isFalse();
    }
}