package com.tanle.meeting_room_booking.team.entity;

import com.tanle.meeting_room_booking.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserTeam {

    @EmbeddedId
    private UserTeamId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("teamId")
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    /** Vị trí/vai trò trong Team do Admin tự nhập (VD: "Backend Dev", "Senior BA", "Tech Lead"...). */
    @Column(name = "member_role", length = 50, nullable = false)
    @Builder.Default
    private String memberRole = "MEMBER";
}
