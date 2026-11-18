package com.iitsm.controller;

import com.iitsm.controller.dto.RegisterForm;
import com.iitsm.domain.enums.Role;
import com.iitsm.domain.model.User;
import com.iitsm.exception.ITSMException;
import com.iitsm.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Handles login, employee self-registration, and role-based home dashboard routing. */
@Controller
public class AuthController {

    private final UserService userService;

    /** Constructs AuthController with UserService. */
    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** Redirects root path to role dashboard. */
    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    /** Redirects authenticated users to their role-specific landing dashboard. */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        User user = userService.getByUsername(authentication.getName());
        if (user.getRole() == Role.ADMIN) {
            return "redirect:/admin/dashboard";
        }
        if (user.getRole() == Role.TECHNICIAN) {
            return "redirect:/technician/dashboard";
        }
        return "redirect:/employee/dashboard";
    }

    /** Renders login page. */
    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    /** Renders employee self-registration form. */
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("form", RegisterForm.empty());
        return "auth/register";
    }

    /** Registers a new Employee account only (never technicians or admins). */
    @PostMapping("/register")
    public String registerEmployee(@Valid @ModelAttribute("form") RegisterForm form,
                                   BindingResult bindingResult,
                                   Model model,
                                   RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerEmployee(
                    form.username(), form.email(), form.fullName(),
                    form.password(), form.phone(), form.department(), form.jobTitle());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Employee account created. Sign in with your credentials.");
            return "redirect:/login";
        } catch (ITSMException ex) {
            model.addAttribute("errorMessage", ex.getMessage());
            return "auth/register";
        }
    }
}
