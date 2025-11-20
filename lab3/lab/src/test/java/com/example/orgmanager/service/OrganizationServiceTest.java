package com.example.orgmanager.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.example.orgmanager.model.Address;
import com.example.orgmanager.model.Coordinates;
import com.example.orgmanager.model.Organization;
import com.example.orgmanager.model.OrganizationType;
import com.example.orgmanager.repository.AddressRepository;
import com.example.orgmanager.repository.CoordinatesRepository;
import com.example.orgmanager.repository.OrganizationRepository;
import com.example.orgmanager.web.dto.OrganizationForm;
import jakarta.validation.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private AddressRepository addressRepository;
    @Mock
    private CoordinatesRepository coordinatesRepository;
    @Mock
    private OrganizationEventPublisher eventPublisher;

    @InjectMocks
    private OrganizationService service;

    @Test
    @DisplayName("averageEmployeesTop10ByTurnover handles nulls "
            + "and averages correctly")
    void averageEmployeesTop10ByTurnover() {
        List<Organization> top10 = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Organization o = new Organization();
            // mix of nulls and values
            o.setEmployeesCount((i % 3 == 0)
                    ? null
                    : (long) (i * 10));
            top10.add(o);
        }
        when(organizationRepository.streamTop10ByOrderByAnnualTurnoverDesc())
                .thenReturn(top10.stream());

        double avg = service.averageEmployeesTop10ByTurnover();
        // employees: 0,10,20,0,40,50,0,70,80,0 -> sum=270, avg=27.0
        assertThat(avg).isEqualTo(27.0);
    }

    @Test
    @DisplayName("list() with type filter returns empty for invalid enum")
    void listWithInvalidType() {
        OrganizationFilter filter = new OrganizationFilter(
                Optional.of("type"),
                Optional.of("NOT_A_TYPE"));

        assertThatThrownBy(() -> service.list(filter, PageRequest.of(0, 5)))
                .isInstanceOf(ValidationException.class);
        verifyNoInteractions(organizationRepository);
    }

    @Test
    @DisplayName("list() with name filter delegates to repository")
    void listWithNameFilter() {
        Page<Organization> expected = new PageImpl<>(
                List.of(new Organization()));
        when(organizationRepository.findByName(eq("Acme"), any()))
                .thenReturn(expected);

        OrganizationFilter filter = new OrganizationFilter(
                Optional.of("name"),
                Optional.of("Acme"));

        Page<?> page = service.list(
                filter,
                PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        verify(organizationRepository).findByName(eq("Acme"), any());
    }

    @Test
    @DisplayName("create() persists new related entities and publishes event")
    void createNewFlow() {
        OrganizationForm form = new OrganizationForm();
        form.setName("Org");
        form.setFullName("Org Full");
        form.setType(OrganizationType.PUBLIC);
        form.setAnnualTurnover(100f);
        form.setEmployeesCount(50L);
        form.setRating(4.2);
        form.setCoordX(1);
        form.setCoordY(2f);
        form.setOfficialStreet("Main");
        form.setOfficialZipCode("1000");
        form.setPostalStreet("Second");
        form.setPostalZipCode("2000");

        // mock saves to return entities with ids
        when(coordinatesRepository.save(any(Coordinates.class)))
                .thenAnswer(inv -> {
            Coordinates coordinates = inv.getArgument(0);
            coordinates.setId(10L);
            return coordinates;
        });
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> {
            Address address = inv.getArgument(0);
            address.setId(address.getId() == null
                    ? 11L
                    : address.getId());
            return address;
        });
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(inv -> {
            Organization organization = inv.getArgument(0);
            organization.setId(1);
            return organization;
        });

        Organization saved = service.create(form);
        assertThat(saved.getId()).isEqualTo(1);
        assertThat(saved.getCoordinates()).isNotNull();
        assertThat(saved.getOfficialAddress()).isNotNull();
        assertThat(saved.getPostalAddress()).isNotNull();

        verify(coordinatesRepository, times(1)).save(any(Coordinates.class));
        verify(addressRepository, times(2)).save(any(Address.class));
        verify(organizationRepository, times(1)).save(any(Organization.class));

        ArgumentCaptor<Integer> idCaptor = ArgumentCaptor.forClass(
                Integer.class);
        verify(eventPublisher).broadcast(eq("created"), idCaptor.capture());
        assertThat(idCaptor.getValue()).isEqualTo(1);
    }
}
