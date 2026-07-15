package lk.backhaulmatch.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "Login_History")
@Getter
@Setter
@NoArgsConstructor
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long loginId;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private LocalDateTime loginTime = LocalDateTime.now();
    private String ipAddress;
    private String deviceInfo;

    @Enumerated(EnumType.STRING)
    private LoginStatus status;

    public enum LoginStatus { SUCCESS, FAILED }
}
