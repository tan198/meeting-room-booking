package com.tanle.meeting_room_booking.permission.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name="id", nullable=false, updatable=false)
    private UUID id;

    @Column(name = "code", length = 100, nullable = false, unique = true, updatable = false)
    private String code;

    @Column(name = "description", length = 255)
    private String description;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Permission that)) {
            return false;
        }

        return code != null && code.equals(that.code);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(code);
    }
}
