package com.codewithalanso.shopforge.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AddressRequest {

    @Size(max = 100, message = "Label cannot exceed 100 characters")
    private String label;

    @NotBlank(message = "First name is required")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    private String firstName;

    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    private String lastName;

    @NotBlank(message = "Phone number is required")
    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String phone;

    @NotBlank(message = "Address line 1 is required")
    @Size(max = 300, message = "Address line 1 cannot exceed 300 characters")
    private String addressLine1;

    @Size(max = 300, message = "Address line 2 cannot exceed 300 characters")
    private String addressLine2;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @NotBlank(message = "Country is required")
    @Size(min = 2, max = 2, message = "Country must be a 2-character ISO code")
    private String country = "IN";

    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    private String postalCode;

    // This field name starting with "is" affects DESERIALIZATION the same way
    // CategoryResponse.isActive's comment describes for serialization: Lombok's setter for this
    // field is setDefault(boolean) (it strips the leading "is" from the property name it derives
    // for the setter too), so without @JsonProperty forcing the JSON key, an incoming
    // {"isDefault": true} would silently fail to bind here and this would always read false.
    @JsonProperty("isDefault")
    private boolean isDefault;
}
