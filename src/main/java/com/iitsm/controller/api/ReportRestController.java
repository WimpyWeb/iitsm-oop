package com.iitsm.controller.api;

import com.iitsm.controller.dto.DashboardReportDto;
import com.iitsm.service.ReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin REST controller exposing operational analytics and recurring problem summaries. */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAnyRole('ADMIN', 'TECHNICIAN')")
public class ReportRestController {

    private final ReportService reportService;

    /** Constructs ReportRestController with ReportService. */
    public ReportRestController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** Returns live operational metrics, SLA compliance, workload, feedback, and recurring problems. */
    @GetMapping("/summary")
    public DashboardReportDto summary() {
        return reportService.generateReport();
    }
}
