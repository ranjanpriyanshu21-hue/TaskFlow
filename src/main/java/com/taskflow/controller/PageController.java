package com.taskflow.controller;

import com.taskflow.service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class PageController {
    private final ProjectService projects;

    @GetMapping("/")
    public String home() {
        return "redirect:/projects";
    }

    @GetMapping("/projects")
    public String list(Model model, Principal principal) {
        model.addAttribute("projects", projects.listFor(principal.getName()));
        model.addAttribute("me", principal.getName());
        return "projects";
    }

    @PostMapping("/projects")
    public String create(@RequestParam String name, @RequestParam(required = false) String description,
                         Principal principal, RedirectAttributes flash) {
        try {
            Long id = projects.create(principal.getName(), name, description);
            return "redirect:/projects/" + id;
        } catch (ResponseStatusException e) {
            flash.addFlashAttribute("error", e.getReason());
            return "redirect:/projects";
        }
    }

    @GetMapping("/projects/{id}")
    public String board(@PathVariable Long id, Model model, Principal principal) {
        model.addAttribute("board", projects.board(id, principal.getName()));
        model.addAttribute("me", principal.getName());
        return "board";
    }

    @PostMapping("/projects/{id}/members")
    public String addMember(@PathVariable Long id, @RequestParam String username,
                            Principal principal, RedirectAttributes flash) {
        try {
            projects.addMember(id, principal.getName(), username);
            flash.addFlashAttribute("message", "Added " + username.trim() + " to the project.");
        } catch (ResponseStatusException e) {
            flash.addFlashAttribute("error", e.getReason());
        }
        return "redirect:/projects/" + id;
    }
}
