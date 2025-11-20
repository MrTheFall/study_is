package com.example.orgmanager.web;

import java.util.List;

import com.example.orgmanager.model.Organization;
import com.example.orgmanager.service.OrganizationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/analytics")
public final class AnalyticsController {
    private final OrganizationService service;

    public AnalyticsController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping
    public String page(
            Model model,
            @RequestParam(value = "ratingEq", required = false)
            Double ratingEq,
            @RequestParam(value = "startsWith", required = false)
            String startsWith,
            @RequestParam(value = "fullNameGt", required = false)
            String fullNameGt) {
        if (ratingEq != null) {
            model.addAttribute(
                    "countRatingEq",
                    service.countByRatingEquals(ratingEq));
            model.addAttribute("ratingEq", ratingEq);
        }
        if (startsWith != null && !startsWith.isBlank()) {
            List<Organization> list = service.fullNameStartsWith(startsWith);
            model.addAttribute("listStartsWith", list);
            model.addAttribute("startsWith", startsWith);
        }
        if (fullNameGt != null && !fullNameGt.isBlank()) {
            List<Organization> list = service.fullNameGreaterThan(fullNameGt);
            model.addAttribute("listFullNameGt", list);
            model.addAttribute("fullNameGt", fullNameGt);
        }
        model.addAttribute("top5", service.top5ByTurnover());
        model.addAttribute(
                "avgTop10Employees",
                service.averageEmployeesTop10ByTurnover());
        return "analytics/index";
    }
}
