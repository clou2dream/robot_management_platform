package com.robotmanagement.openplatform.controller;

import com.robotmanagement.openplatform.dto.OpenPlatformCredentialResponse;
import com.robotmanagement.openplatform.dto.OpenPlatformCredentialStatusResponse;
import com.robotmanagement.openplatform.dto.SaveOpenPlatformCredentialRequest;
import com.robotmanagement.openplatform.service.OpenPlatformCredentialService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/account/open-platform-credentials")
public class OpenPlatformCredentialController {

    private final OpenPlatformCredentialService credentialService;

    public OpenPlatformCredentialController(OpenPlatformCredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @GetMapping("/status")
    public OpenPlatformCredentialStatusResponse status() {
        return credentialService.status();
    }

    @GetMapping
    public List<OpenPlatformCredentialResponse> listCredentials() {
        return credentialService.listCredentials();
    }

    @PostMapping
    public OpenPlatformCredentialResponse createCredential(
        @Valid @RequestBody SaveOpenPlatformCredentialRequest request
    ) {
        return credentialService.createCredential(request);
    }

    @PutMapping("/{credentialId}")
    public OpenPlatformCredentialResponse updateCredential(
        @PathVariable UUID credentialId,
        @Valid @RequestBody SaveOpenPlatformCredentialRequest request
    ) {
        return credentialService.updateCredential(credentialId, request);
    }

    @DeleteMapping("/{credentialId}")
    public void deleteCredential(@PathVariable UUID credentialId) {
        credentialService.deleteCredential(credentialId);
    }
}
