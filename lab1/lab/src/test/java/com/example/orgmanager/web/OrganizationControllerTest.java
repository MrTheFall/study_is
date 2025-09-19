package com.example.orgmanager.web;

import java.util.List;

import com.example.orgmanager.model.Organization;
import com.example.orgmanager.service.OrganizationFilter;
import com.example.orgmanager.service.OrganizationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = OrganizationController.class)
class OrganizationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrganizationService service;

    @Test
    @DisplayName("GET / redirects to /organizations")
    void rootRedirect() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/organizations"));
    }

    @Test
    @DisplayName("GET /organizations renders index view and calls service")
    void organizationsList() throws Exception {
        Page<Organization> page = new PageImpl<>(List.of());
        given(service.list(any(OrganizationFilter.class), any(PageRequest.class)))
                .willReturn(page);

        mockMvc.perform(get("/organizations"))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/index"))
                .andExpect(model().attributeExists("page"));

        ArgumentCaptor<OrganizationFilter> captor = ArgumentCaptor.forClass(OrganizationFilter.class);
        verify(service).list(
                captor.capture(),
                any(PageRequest.class));

        OrganizationFilter filter = captor.getValue();
        assertThat(filter.field()).isEmpty();
        assertThat(filter.value()).isEmpty();
    }

    @Test
    @DisplayName("GET /organizations/new renders create form with lookups")
    void createForm() throws Exception {
        given(service.allCoordinates()).willReturn(List.of());
        given(service.allAddresses()).willReturn(List.of());

        mockMvc.perform(get("/organizations/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("organizations/create"))
                .andExpect(model().attributeExists(
                        "form",
                        "coords",
                        "addresses",
                        "types"));
    }
}
