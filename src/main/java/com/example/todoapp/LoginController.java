package com.example.todoapp;

import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LoginController {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public LoginController(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String login() {
        return userMapper.countUsers() == 0 ? "redirect:/setup" : "login";
    }

    @GetMapping("/setup")
    public String setup(Model model) {
        if (userMapper.countUsers() > 0) return "redirect:/login";
        model.addAttribute("setupForm", new SetupForm());
        return "setup";
    }

    @PostMapping("/setup")
    public String createInitialUser(@Valid @ModelAttribute SetupForm setupForm, BindingResult errors) {
        if (userMapper.countUsers() > 0) return "redirect:/login";
        if (errors.hasErrors()) return "setup";
        try {
            userMapper.insertInitialUser(setupForm.getUsername(), passwordEncoder.encode(setupForm.getPassword()));
        } catch (DuplicateKeyException alreadyInitialized) {
            return "redirect:/login";
        }
        return "redirect:/login?setup=true";
    }
}
