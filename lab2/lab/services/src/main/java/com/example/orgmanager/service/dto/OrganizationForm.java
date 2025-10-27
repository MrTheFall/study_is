package com.example.orgmanager.service.dto;

import com.example.orgmanager.model.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public final class OrganizationForm {
    private Integer id;

    @NotNull
    @NotBlank
    private String name;

    @NotNull
    @NotBlank
    private String fullName;

    private OrganizationType type; // can be null

    @Positive
    private float annualTurnover;

    @NotNull
    @Positive
    private Long employeesCount;

    @Positive
    private double rating;

    // Coordinates
    private Long coordinatesId; // optional existing
    private Integer coordX; // for new
    private Float coordY; // for new

    // Official address
    private Long officialAddressId;
    private String officialStreet;
    private String officialZipCode; // nullable

    // Postal address
    private Long postalAddressId;
    private String postalStreet;
    private String postalZipCode;

    // UI helper
    private boolean postalSameAsOfficial;

}
