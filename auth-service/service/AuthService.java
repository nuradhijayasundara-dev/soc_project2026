package lk.backhaulmatch.auth.service;

import lk.backhaulmatch.auth.dto.AuthResponse;
import lk.backhaulmatch.auth.dto.LoginRequest;
import lk.backhaulmatch.auth.dto.RegisterRequest;
import lk.backhaulmatch.auth.entity.Role;
import lk.backhaulmatch.auth.entity.User;
import lk.backhaulmatch.auth.entity.UserType;
import lk.backhaulmatch.auth.repository.RoleRepository;
import lk.backhaulmatch.auth.repository.UserRepository;
import lk.backhaulmatch.auth.security.JwtUtil;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                        RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder,
                        JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setUserType(UserType.valueOf(request.getUserType()));

        // Assign the matching default role (create it on first use if missing)
        Role role = roleRepository.findByRoleName(request.getUserType())
                .orElseGet(() -> roleRepository.save(new Role(request.getUserType())));
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);

        User saved = userRepository.save(user);
        return buildAuthResponse(saved);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password");
        }

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtUtil.generateToken(user);
        List<String> roleNames = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());
        return new AuthResponse(token, user.getUserId(), user.getUsername(), roleNames);
    }
}
