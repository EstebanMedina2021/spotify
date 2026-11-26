package com.playlist.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.isEnabled()
        );
    }

    public AuthenticatedUser loadUserById(UUID id) {
    return userRepository.findById(id)
            .map(u -> new AuthenticatedUser(u.getId(), u.getUsername(), u.getPasswordHash(), u.isEnabled()))
            .orElseThrow(() -> new UsernameNotFoundException("User not found by id: " + id));
}
}