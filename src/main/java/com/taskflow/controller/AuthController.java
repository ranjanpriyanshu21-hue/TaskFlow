package com.taskflow.controller;

import com.taskflow.model.User;
import com.taskflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String fullName, @RequestParam String username,
                           @RequestParam String password, Model model) {
        String error = null;
        username = username.trim();
        if (fullName.isBlank()) error = "Enter your name.";
        else if (!username.matches("[A-Za-z0-9_.-]{3,30}"))
            error = "Usernames are 3-30 characters: letters, numbers, dot, dash or underscore.";
        else if (password.length() < 6) error = "Use a password of at least 6 characters.";
        else if (users.existsByUsername(username)) error = "That username is taken. Try another.";

        if (error != null) {
            model.addAttribute("error", error);
            model.addAttribute("fullName", fullName);
            model.addAttribute("username", username);
            return "register";
        }
        User u = new User();
        u.setFullName(fullName.trim());
        u.setUsername(username);
        u.setPassword(encoder.encode(password));
        users.save(u);
        return "redirect:/login?registered";
    }
}
