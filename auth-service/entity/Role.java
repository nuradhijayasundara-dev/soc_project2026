package lk.backhaulmatch.auth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Roles")
@Getter
@Setter
@NoArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer roleId;

    @Column(name = "role_name", nullable = false, unique = true, length = 50)
    private String roleName;   // ADMIN, FLEET_MANAGER, COURIER_MANAGER, DRIVER, CUSTOMER

    private String description;

    public Role(String roleName) {
        this.roleName = roleName;
    }
}
