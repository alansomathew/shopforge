package com.codewithalanso.shopforge.auth.service;

import com.codewithalanso.shopforge.auth.dto.AddressRequest;
import com.codewithalanso.shopforge.auth.dto.ChangePasswordRequest;
import com.codewithalanso.shopforge.auth.dto.UpdateProfileRequest;
import com.codewithalanso.shopforge.auth.dto.UserResponse;
import com.codewithalanso.shopforge.common.exception.AppException;
import java.util.stream.Collectors;
import com.codewithalanso.shopforge.entities.Address;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.repositories.AddressRepository;
import com.codewithalanso.shopforge.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (request.getPhone() != null && !request.getPhone().equals(user.getPhone())) {
            if (userRepository.existsByPhone(request.getPhone())) {
                throw new AppException("Phone number is already registered by another account", HttpStatus.BAD_REQUEST);
            }
            user.setPhone(request.getPhone());
            user.setPhoneVerified(false); // require re-verification if changed
        }

        user.setFirstName(request.getFirstName().trim());
        user.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        userRepository.save(user);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (user.getPasswordHash() == null) {
            throw new AppException("Social login accounts do not have password credentials", HttpStatus.BAD_REQUEST);
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException("Incorrect current password", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public List<Address> getAddresses(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        return addressRepository.findByUser(user);
    }

    @Transactional
    public Address createAddress(UUID userId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (request.isDefault()) {
            resetDefaultAddresses(user);
        }

        Address address = Address.builder()
                .user(user)
                .label(request.getLabel() != null ? request.getLabel().trim() : "Home")
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName() != null ? request.getLastName().trim() : null)
                .phone(request.getPhone().trim())
                .addressLine1(request.getAddressLine1().trim())
                .addressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null)
                .city(request.getCity().trim())
                .state(request.getState().trim())
                .country(request.getCountry().trim().toUpperCase())
                .postalCode(request.getPostalCode().trim())
                .isDefault(request.isDefault())
                .build();

        return addressRepository.save(address);
    }

    @Transactional
    public Address updateAddress(UUID userId, UUID addressId, AddressRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Address address = addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new AppException("Address not found or does not belong to you", HttpStatus.NOT_FOUND));

        if (request.isDefault() && !address.isDefault()) {
            resetDefaultAddresses(user);
        }

        address.setLabel(request.getLabel() != null ? request.getLabel().trim() : "Home");
        address.setFirstName(request.getFirstName().trim());
        address.setLastName(request.getLastName() != null ? request.getLastName().trim() : null);
        address.setPhone(request.getPhone().trim());
        address.setAddressLine1(request.getAddressLine1().trim());
        address.setAddressLine2(request.getAddressLine2() != null ? request.getAddressLine2().trim() : null);
        address.setCity(request.getCity().trim());
        address.setState(request.getState().trim());
        address.setCountry(request.getCountry().trim().toUpperCase());
        address.setPostalCode(request.getPostalCode().trim());
        address.setDefault(request.isDefault());

        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Address address = addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new AppException("Address not found or does not belong to you", HttpStatus.NOT_FOUND));

        addressRepository.delete(address);
    }

    private void resetDefaultAddresses(User user) {
        List<Address> addresses = addressRepository.findByUser(user);
        for (Address addr : addresses) {
            if (addr.isDefault()) {
                addr.setDefault(false);
                addressRepository.save(addr);
            }
        }
    }

    /**
     * Get all users in the system mapped to UserResponse DTO.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .avatarUrl(user.getAvatarUrl())
                        .roles(user.getRoles().stream()
                                .map(r -> r.getRole().name())
                                .collect(Collectors.toSet()))
                        .build())
                .collect(Collectors.toList());
    }
}
