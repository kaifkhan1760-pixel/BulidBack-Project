package com.buildback.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.buildback.repository.UserRepository;
import com.buildback.service.EmailUtils;

/**
 * Spring Security calls this during login to find the account by email.
 * Spring then compares the typed password with the stored BCrypt hash itself.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        // Same normalization as registration: " Ali@Example.com " finds "ali@example.com"
        return userRepository.findByEmail(EmailUtils.normalize(email))
                .map(AppUserDetails::from)
                // Spring turns this into the same generic "bad credentials" error as a wrong password
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }
}
