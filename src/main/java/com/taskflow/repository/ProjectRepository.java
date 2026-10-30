package com.taskflow.repository;

import com.taskflow.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @Query("select p from Project p join p.members m where m.id = :userId order by p.createdAt desc")
    List<Project> findByMember(@Param("userId") Long userId);
}
