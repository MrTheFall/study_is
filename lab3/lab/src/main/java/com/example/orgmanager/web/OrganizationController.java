package com.example.orgmanager.web;

import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.service.OrganizationFilter;
import com.example.orgmanager.service.OrganizationService;
import com.example.orgmanager.web.dto.OrganizationForm;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.ValidationException;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public final class OrganizationController {
    private static final int MAX_PAGE_SIZE = 100;

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/organizations";
    }

    @GetMapping("/organizations")
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
    @RequestParam(required = false) String dir,
    @RequestParam Optional<String> filterField,
    @RequestParam Optional<String> filterValue,
    Model model) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        OrganizationFilter filter = new OrganizationFilter(filterField, filterValue);
        Page<Organization> data = service.list(filter, pageable);
        model.addAttribute("page", data);
        model.addAttribute("filterField", filter.field().orElse(null));
        model.addAttribute("filterValue", filter.value().orElse(null));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        model.addAttribute("types", OrganizationType.values());
        return "organizations/index";
    }

    @GetMapping("/organizations/table")
    public String table(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String dir,
            @RequestParam Optional<String> filterField,
            @RequestParam Optional<String> filterValue,
            Model model) {
        Pageable pageable = buildPageable(page, size, sort, dir);
        OrganizationFilter filter = new OrganizationFilter(filterField, filterValue);
        Page<Organization> data = service.list(filter, pageable);
        model.addAttribute("page", data);
        model.addAttribute("filterField", filter.field().orElse(null));
        model.addAttribute("filterValue", filter.value().orElse(null));
        model.addAttribute("sort", sort);
        model.addAttribute("dir", dir);
        return "organizations/_table :: table";
    }

    @GetMapping("/organizations/new")
    public String createForm(Model model) {
        OrganizationForm form = new OrganizationForm();
        model.addAttribute("form", form);
        model.addAttribute("coords", service.allCoordinates());
        model.addAttribute("addresses", service.allAddresses());
        model.addAttribute("types", OrganizationType.values());
        return "organizations/create";
    }

    @PostMapping("/organizations")
    public String create(@Valid @ModelAttribute("form") OrganizationForm form,
            BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("coords", service.allCoordinates());
            model.addAttribute("addresses", service.allAddresses());
            model.addAttribute("types", OrganizationType.values());
            return "organizations/create";
        }
        Organization saved = service.create(form);
        return "redirect:/organizations/" + saved.getId();
    }

    @GetMapping("/organizations/{id}")
    public String show(@PathVariable Integer id, Model model) {
        Organization org = service.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        model.addAttribute("org", org);
        return "organizations/show";
    }

    @GetMapping("/organizations/{id}/edit")
    public String editForm(@PathVariable Integer id, Model model) {
        Organization org = service.findById(id)
                .orElseThrow(EntityNotFoundException::new);
        OrganizationForm form = toForm(org);
        model.addAttribute("form", form);
        model.addAttribute("orgId", id);
        model.addAttribute("coords", service.allCoordinates());
        model.addAttribute("addresses", service.allAddresses());
        model.addAttribute("types", OrganizationType.values());
        return "organizations/edit";
    }

    @PostMapping("/organizations/{id}")
    public String update(@PathVariable Integer id,
            @Valid @ModelAttribute("form") OrganizationForm form,
            BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            model.addAttribute("orgId", id);
            model.addAttribute("coords", service.allCoordinates());
            model.addAttribute("addresses", service.allAddresses());
            model.addAttribute("types", OrganizationType.values());
            return "organizations/edit";
        }
        service.update(id, form);
        return "redirect:/organizations/" + id;
    }

    @PostMapping("/organizations/{id}/delete")
    public String delete(@PathVariable Integer id) {
        service.delete(id);
        return "redirect:/organizations";
    }

    private Pageable buildPageable(
            int page,
            int size,
            String sort,
            String dir) {
        int safePage = Math.max(page, 0);
        int safeSize = validatePageSize(size);
        if (sort == null || sort.isBlank()) {
            return PageRequest.of(safePage, safeSize);
        }
        Sort.Direction direction = (dir != null && dir.equalsIgnoreCase("desc"))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        // only allow sorting by known simple fields
        List<String> allowed = List.of(
                "id",
                "name",
                "fullName",
                "annualTurnover",
                "employeesCount",
                "rating",
                "type");
        if (!allowed.contains(sort)) {
            return PageRequest.of(safePage, safeSize);
        }
        return PageRequest.of(
                safePage,
                safeSize,
                Sort.by(direction, sort));
    }

    private int validatePageSize(int requestedSize) {
        if (requestedSize < 1) {
            throw new ValidationException("Размер страницы должен быть положительным");
        }
        if (requestedSize > MAX_PAGE_SIZE) {
            throw new ValidationException(
                    "Размер страницы не может превышать " + MAX_PAGE_SIZE);
        }
        return requestedSize;
    }

    private OrganizationForm toForm(Organization org) {
        OrganizationForm f = new OrganizationForm();
        f.setId(org.getId());
        f.setName(org.getName());
        f.setFullName(org.getFullName());
        f.setType(org.getType());
        f.setAnnualTurnover(org.getAnnualTurnover());
        f.setEmployeesCount(org.getEmployeesCount());
        f.setRating(org.getRating());
        if (org.getCoordinates() != null) {
            f.setCoordinatesId(org.getCoordinates().getId());
            f.setCoordX(org.getCoordinates().getX());
            f.setCoordY(org.getCoordinates().getY());
        }
        if (org.getOfficialAddress() != null) {
            f.setOfficialAddressId(org.getOfficialAddress().getId());
            f.setOfficialStreet(org.getOfficialAddress().getStreet());
            f.setOfficialZipCode(org.getOfficialAddress().getZipCode());
        }
        if (org.getPostalAddress() != null) {
            f.setPostalAddressId(org.getPostalAddress().getId());
            f.setPostalStreet(org.getPostalAddress().getStreet());
            f.setPostalZipCode(org.getPostalAddress().getZipCode());
        }
        if (org.getOfficialAddress() != null
                && org.getPostalAddress() != null
                && org.getOfficialAddress().getId() != null
                && org.getPostalAddress().getId() != null
                && org.getOfficialAddress().getId()
                        .equals(org.getPostalAddress().getId())) {
            f.setPostalSameAsOfficial(true);
        }
        return f;
    }
}
