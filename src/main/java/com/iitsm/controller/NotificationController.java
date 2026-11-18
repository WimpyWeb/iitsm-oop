package com.iitsm.controller;

import com.iitsm.domain.model.User;
import com.iitsm.service.NotificationService;
import com.iitsm.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Controller serving notification history and HTMX-polled bell fragments. */
@Controller
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserService userService;

    /** Constructs NotificationController with NotificationService and UserService. */
    public NotificationController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    /** Displays full paginated notification history across channels for the current user. */
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("notifications",
                notificationService.getAllForUser(user.getUserCode(), PageRequest.of(page, 20)));
        return "notifications/list";
    }

    /** Returns the HTMX notification bell fragment updated via polling. */
    @GetMapping("/bell")
    public String bellFragment(Authentication authentication, Model model) {
        User user = userService.getByUsername(authentication.getName());
        model.addAttribute("unreadCount", notificationService.getUnreadCount(user.getUserCode()));
        model.addAttribute("recentNotifications", notificationService.getRecentInApp(user.getUserCode()));
        return "notifications/fragments :: bell";
    }

    /** Marks a single notification read and returns either updated bell fragment or redirect. */
    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id,
                           @RequestHeader(value = "HX-Request", required = false) String hxRequest,
                           Authentication authentication,
                           Model model) {
        User user = userService.getByUsername(authentication.getName());
        notificationService.markAsRead(id, user.getUserCode());
        if ("true".equalsIgnoreCase(hxRequest)) {
            return bellFragment(authentication, model);
        }
        return "redirect:/notifications";
    }

    /** Marks all unread in-app notifications read for the signed-in user. */
    @PostMapping("/read-all")
    public String markAllRead(@RequestHeader(value = "HX-Request", required = false) String hxRequest,
                              Authentication authentication,
                              Model model) {
        User user = userService.getByUsername(authentication.getName());
        notificationService.markAllRead(user.getUserCode());
        if ("true".equalsIgnoreCase(hxRequest)) {
            return bellFragment(authentication, model);
        }
        return "redirect:/notifications";
    }
}
