package lk.backhaulmatch.courier.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Receivers")
@Getter
@Setter
@NoArgsConstructor
public class Receiver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long receiverId;

    @Column(nullable = false, length = 120)
    private String receiverName;

    @Column(nullable = false, length = 20)
    private String phoneNumber;

    private Integer cityId; // reference only — location-service

    @Column(nullable = false)
    private String address;
}
