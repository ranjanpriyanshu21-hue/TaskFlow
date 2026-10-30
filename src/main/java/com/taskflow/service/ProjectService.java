package com.taskflow.service;

import com.taskflow.dto.Dtos.MemberDto;
import com.taskflow.model.Project;
import com.taskflow.model.TaskStatus;
import com.taskflow.model.User;
import com.taskflow.repository.ProjectRepository;
import com.taskflow.repository.TaskRepository;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final UserRepository users;
    private final TaskRepository tasks;
    private final NotificationService notifications;

    public record ProjectSummary(Long id, String name, String description, String ownerName,
                                 int memberCount, long taskCount, long doneCount) {
        public int percentDone() {
            return taskCount == 0 ? 0 : (int) Math.round(doneCount * 100.0 / taskCount);
        }
    }

    public record BoardInfo(Long id, String name, String description, boolean owner, List<MemberDto> members) {}

    @Transactional(readOnly = true)
    public List<ProjectSummary> listFor(String username) {
        User me = user(username);
        return projects.findByMember(me.getId()).stream()
                .map(p -> new ProjectSummary(p.getId(), p.getName(), p.getDescription(),
                        p.getOwner().getFullName(), p.getMembers().size(),
                        tasks.countByProjectId(p.getId()),
                        tasks.countByProjectIdAndStatus(p.getId(), TaskStatus.DONE)))
                .toList();
    }

    @Transactional
    public Long create(String username, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Give the project a name.");
        }
        User me = user(username);
        Project p = new Project();
        p.setName(name.trim());
        p.setDescription(description == null ? null : description.trim());
        p.setOwner(me);
        p.getMembers().add(me);
        return projects.save(p).getId();
    }

    @Transactional(readOnly = true)
    public BoardInfo board(Long projectId, String username) {
        Project p = requireMember(projectId, username);
        return new BoardInfo(p.getId(), p.getName(), p.getDescription(),
                p.getOwner().getUsername().equals(username), memberDtos(p));
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members(Long projectId, String username) {
        return memberDtos(requireMember(projectId, username));
    }

    @Transactional
    public void addMember(Long projectId, String ownerUsername, String newMemberUsername) {
        Project p = requireMember(projectId, ownerUsername);
        if (!p.getOwner().getUsername().equals(ownerUsername)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the project owner can add people.");
        }
        User added = users.findByUsername(newMemberUsername == null ? "" : newMemberUsername.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No user with that username. Ask them to register first."));
        if (!p.getMembers().add(added)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, added.getFullName() + " is already on this project.");
        }
        notifications.send(added, "You were added to the project \"" + p.getName() + "\"", p.getId());
    }

    /** Loads the project and fails with 404/403 unless the user belongs to it. */
    @Transactional(readOnly = true)
    public Project requireMember(Long projectId, String username) {
        Project p = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found."));
        boolean member = p.getMembers().stream().anyMatch(m -> m.getUsername().equals(username));
        if (!member) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this project.");
        }
        return p;
    }

    private List<MemberDto> memberDtos(Project p) {
        return p.getMembers().stream()
                .sorted(Comparator.comparing(User::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> new MemberDto(m.getId(), m.getUsername(), m.getFullName()))
                .toList();
    }

    private User user(String username) {
        return users.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
    }
}
