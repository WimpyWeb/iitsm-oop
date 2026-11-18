package com.iitsm.controller;

import com.iitsm.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** MVC controller rendering Chart.js analytics and HTMX live-refresh KPI reports. */
@Controller
@RequestMapping("/reports")
@PreAuthorize("hasAnyRole('TECHNICIAN', 'ADMIN')")
public class ReportController {

    private final ReportService reportService;

    /** Constructs ReportController with ReportService. */
    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Renders full analytics dashboard with Chart.js visualizations and recurring problems. */
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("report", reportService.generateReport());
        return "reports/dashboard";
    }

    /** Returns HTMX live-refresh fragment for KPI summary cards and recurring problems. */
    @GetMapping("/kpi-fragment")
    public String kpiFragment(Model model) {
        model.addAttribute("report", reportService.generateReport());
        return "reports/dashboard :: kpiSection";
    }
}
