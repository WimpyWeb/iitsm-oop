package com.iitsm.controller;

import com.iitsm.domain.model.TeamLead;
import com.iitsm.domain.model.User;
import com.iitsm.service.NotificationService;
import com.iitsm.service.UserService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Injects authenticated user identity and unread notification count into all MVC views. */
@ControllerAdvice(annotations = Controller.class)
public class WebModelAdvice {

    private final UserService userService;
    private final NotificationService notificationService;

    /** Constructs WebModelAdvice with UserService and NotificationService. */
    public WebModelAdvice(UserService userService, NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /** Populates global navbar attributes when a user session is authenticated. */
    @ModelAttribute
    public void addGlobalAttributes(Authentication authentication, Model model) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return;
        }
        userService.findByUsername(authentication.getName()).ifPresent(user -> {
            model.addAttribute("currentUser", user);
            model.addAttribute("currentUserCode", user.getUserCode());
            model.addAttribute("currentUserRole", user.getRole().name());
            model.addAttribute("isTeamLead", user instanceof TeamLead);
            model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getUserCode()));
        });
    }
}
