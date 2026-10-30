package com.tanle.meeting_room_booking.group.service;

import com.tanle.meeting_room_booking.common.exception.BusinessException;
import com.tanle.meeting_room_booking.common.exception.ErrorCode;
import com.tanle.meeting_room_booking.group.dto.AddGroupMembersRequest;
import com.tanle.meeting_room_booking.group.dto.CreateGroupRequest;
import com.tanle.meeting_room_booking.group.dto.GroupResponse;
import com.tanle.meeting_room_booking.group.entity.UserGroup;
import com.tanle.meeting_room_booking.group.entity.UserGroupId;
import com.tanle.meeting_room_booking.group.entity.WorkGroup;
import com.tanle.meeting_room_booking.group.repository.UserGroupRepository;
import com.tanle.meeting_room_booking.group.repository.WorkGroupRepository;
import com.tanle.meeting_room_booking.user.entity.User;
import com.tanle.meeting_room_booking.user.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private final WorkGroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final UserRepo userRepo;

    public List<GroupResponse> findAll(String type) {
        List<WorkGroup> groups = (type != null && !type.isBlank())
                ? groupRepository.findByTypeIgnoreCaseOrderByCodeAsc(type.trim())
                : groupRepository.findAll(Sort.by(Sort.Order.asc("code")));
        if (groups.isEmpty()) {
            return List.of();
        }
        List<UUID> groupIds = groups.stream().map(WorkGroup::getId).toList();
        var membersByGroup = userGroupRepository.findById_GroupIdIn(groupIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(ug -> ug.getGroup().getId()));
        return groups.stream()
                .map(g -> GroupResponse.from(g, membersByGroup.getOrDefault(g.getId(), List.of())))
                .toList();
    }

    public GroupResponse getById(UUID id) {
        WorkGroup group = findOrThrow(id);
        return GroupResponse.from(group, userGroupRepository.findById_GroupId(id));
    }

    public List<GroupResponse.GroupMemberDto> getMembers(UUID groupId) {
        findOrThrow(groupId);
        return userGroupRepository.findById_GroupId(groupId)
                .stream()
                .map(GroupResponse.GroupMemberDto::from)
                .toList();
    }

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest rq) {
        String code = rq.code().trim().toUpperCase(Locale.ROOT);
        if (groupRepository.existsByCodeIgnoreCase(code)) {
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "Mã Group '" + code + "' đã tồn tại");
        }

        String type = (rq.type() != null && !rq.type().isBlank())
                ? rq.type().trim().toUpperCase(Locale.ROOT)
                : "PROJECT";

        WorkGroup group = WorkGroup.builder()
                .code(code)
                .name(rq.name().trim())
                .type(type)
                .description(rq.description() != null ? rq.description().trim() : null)
                .build();

        WorkGroup saved = groupRepository.saveAndFlush(group);

        if (rq.memberUserIds() != null && !rq.memberUserIds().isEmpty()) {
            assignMembersInternal(saved, rq.memberUserIds());
        }

        return GroupResponse.from(saved, userGroupRepository.findById_GroupId(saved.getId()));
    }

    @Transactional
    public GroupResponse addMembers(UUID groupId, AddGroupMembersRequest rq) {
        WorkGroup group = findOrThrow(groupId);
        assignMembersInternal(group, rq.userIds());
        return GroupResponse.from(group, userGroupRepository.findById_GroupId(groupId));
    }

    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        findOrThrow(groupId);
        userGroupRepository.deleteById(new UserGroupId(userId, groupId));
    }

    public WorkGroup findOrThrow(UUID id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy Dự án / Phòng ban (Group)"));
    }

    private void assignMembersInternal(WorkGroup group, Set<UUID> userIds) {
        List<User> users = userRepo.findAllById(userIds);
        if (users.size() != userIds.size()) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND, "Một hoặc nhiều người dùng không tồn tại");
        }
        List<UserGroup> entities = new ArrayList<>(users.size());
        for (User user : users) {
            UserGroupId id = new UserGroupId(user.getId(), group.getId());
            if (!userGroupRepository.existsById(id)) {
                entities.add(UserGroup.builder()
                        .id(id)
                        .user(user)
                        .group(group)
                        .build());
            }
        }
        if (!entities.isEmpty()) {
            userGroupRepository.saveAllAndFlush(entities);
        }
    }
}
