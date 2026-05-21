package com.robotmanagement.openplatform.service;

import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.util.HashUtils;
import com.robotmanagement.openplatform.dto.OpenPlatformCredentialResponse;
import com.robotmanagement.openplatform.dto.SaveOpenPlatformCredentialRequest;
import com.robotmanagement.openplatform.entity.OpenPlatformCredentialEntity;
import com.robotmanagement.openplatform.mapper.OpenPlatformCredentialMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenPlatformCredentialServiceTest {

    private final OpenPlatformCredentialMapper credentialMapper = mock(OpenPlatformCredentialMapper.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();

    private OpenPlatformCredentialService credentialService;

    @BeforeEach
    void setUp() {
        credentialService = new OpenPlatformCredentialService(credentialMapper);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUser(operatorId, tenantId, "operator01", "operator", "Default Tenant", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))
            )
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createCredentialStoresCurrentUserScopedCredential() {
        when(credentialMapper.selectCount(any())).thenReturn(0L);
        ArgumentCaptor<OpenPlatformCredentialEntity> credentialCaptor =
            ArgumentCaptor.forClass(OpenPlatformCredentialEntity.class);

        OpenPlatformCredentialResponse response = credentialService.createCredential(
            new SaveOpenPlatformCredentialRequest(" 鍗庝笢鍥尯 ", " app-001 ", " key-001 ", " secret-001 ")
        );

        verify(credentialMapper).insert(credentialCaptor.capture());
        OpenPlatformCredentialEntity credential = credentialCaptor.getValue();
        assertThat(credential.getTenantId()).isEqualTo(tenantId);
        assertThat(credential.getOperatorId()).isEqualTo(operatorId);
        assertThat(credential.getDisplayName()).isEqualTo("鍗庝笢鍥尯");
        assertThat(credential.getAppId()).isEqualTo("app-001");
        assertThat(credential.getApiKey()).isEqualTo("key-001");
        assertThat(credential.getApiSecret()).isEqualTo(HashUtils.sha256Hex("secret-001"));
        assertThat(response.apiKeyMasked()).isEqualTo("key****001");
    }

    @Test
    void createCredentialRejectsDuplicateAppIdAndApiKey() {
        when(credentialMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> credentialService.createCredential(
            new SaveOpenPlatformCredentialRequest(null, "app-001", "key-001", "secret-001")
        ))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT)
            );
    }
}
