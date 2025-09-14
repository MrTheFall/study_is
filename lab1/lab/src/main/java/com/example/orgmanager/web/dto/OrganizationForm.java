package com.example.orgmanager.web.dto;

import com.example.orgmanager.model.OrganizationType;
import jakarta.validation.constraints.*;

public class OrganizationForm {
    private Integer id;

    @NotNull @NotBlank
    private String name;

    @NotNull @NotBlank
    private String fullName;

    private OrganizationType type; // can be null

    @Positive
    private float annualTurnover;

    @NotNull @Positive
    private Long employeesCount;

    @Positive
    private double rating;

    // Coordinates
    private Long coordinatesId; // optional existing
    private Integer coordX; // for new
    private Float coordY;   // for new

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

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public OrganizationType getType() { return type; }
    public void setType(OrganizationType type) { this.type = type; }
    public float getAnnualTurnover() { return annualTurnover; }
    public void setAnnualTurnover(float annualTurnover) { this.annualTurnover = annualTurnover; }
    public Long getEmployeesCount() { return employeesCount; }
    public void setEmployeesCount(Long employeesCount) { this.employeesCount = employeesCount; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public Long getCoordinatesId() { return coordinatesId; }
    public void setCoordinatesId(Long coordinatesId) { this.coordinatesId = coordinatesId; }
    public Integer getCoordX() { return coordX; }
    public void setCoordX(Integer coordX) { this.coordX = coordX; }
    public Float getCoordY() { return coordY; }
    public void setCoordY(Float coordY) { this.coordY = coordY; }
    public Long getOfficialAddressId() { return officialAddressId; }
    public void setOfficialAddressId(Long officialAddressId) { this.officialAddressId = officialAddressId; }
    public String getOfficialStreet() { return officialStreet; }
    public void setOfficialStreet(String officialStreet) { this.officialStreet = officialStreet; }
    public String getOfficialZipCode() { return officialZipCode; }
    public void setOfficialZipCode(String officialZipCode) { this.officialZipCode = officialZipCode; }
    public Long getPostalAddressId() { return postalAddressId; }
    public void setPostalAddressId(Long postalAddressId) { this.postalAddressId = postalAddressId; }
    public String getPostalStreet() { return postalStreet; }
    public void setPostalStreet(String postalStreet) { this.postalStreet = postalStreet; }
    public String getPostalZipCode() { return postalZipCode; }
    public void setPostalZipCode(String postalZipCode) { this.postalZipCode = postalZipCode; }
    public boolean isPostalSameAsOfficial() { return postalSameAsOfficial; }
    public void setPostalSameAsOfficial(boolean postalSameAsOfficial) { this.postalSameAsOfficial = postalSameAsOfficial; }
}
