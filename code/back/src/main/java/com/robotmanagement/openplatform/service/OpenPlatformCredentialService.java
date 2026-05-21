package com.robotmanagement.openplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.common.security.SecurityUtils;
import com.robotmanagement.common.util.HashUtils;
import com.robotmanagement.openplatform.dto.OpenPlatformCredentialResponse;
import com.robotmanagement.openplatform.dto.OpenPlatformCredentialStatusResponse;
import com.robotmanagement.openplatform.dto.SaveOpenPlatformCredentialRequest;
import com.robotmanagement.openplatform.entity.OpenPlatformCredentialEntity;
import com.robotmanagement.openplatform.mapper.OpenPlatformCredentialMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class OpenPlatformCredentialService {

    private final OpenPlatformCredentialMapper credentialMapper;

    public OpenPlatformCredentialService(OpenPlatformCredentialMapper credentialMapper) {
        this.credentialMapper = credentialMapper;
    }

    public OpenPlatformCredentialStatusResponse status() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        long count = credentialMapper.selectCount(baseWrapper(currentUser));
        return new OpenPlatformCredentialStatusResponse(count > 0, count);
    }

    public List<OpenPlatformCredentialResponse> listCredentials() {
        CurrentUser currentUser = SecurityUtils.currentUser();
        return listCredentialEntities(currentUser)
            .stream()
            .map(OpenPlatformCredentialResponse::from)
            .toList();
    }

    public List<OpenPlatformCredentialEntity> listCurrentUserCredentialEntities() {
        return listCredentialEntities(SecurityUtils.currentUser());
    }

    public List<OpenPlatformCredentialEntity> listCredentialEntities(CurrentUser currentUser) {
        return credentialMapper.selectList(
            baseWrapper(currentUser).orderByDesc(OpenPlatformCredentialEntity::getCreatedAt)
        );
    }

    @Transactional
    public OpenPlatformCredentialResponse createCredential(SaveOpenPlatformCredentialRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        assertUnique(currentUser, null, request.appId(), request.apiKey());

        OffsetDateTime now = OffsetDateTime.now();
        OpenPlatformCredentialEntity credential = new OpenPlatformCredentialEntity();
        credential.setId(UUID.randomUUID());
        credential.setTenantId(currentUser.tenantId());
        credential.setOperatorId(currentUser.operatorId());
        credential.setDisplayName(trimToNull(request.displayName()));
        credential.setAppId(request.appId().trim());
        credential.setApiKey(request.apiKey().trim());
        credential.setApiSecret(HashUtils.sha256Hex(request.apiSecret().trim()));
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);

        credentialMapper.insert(credential);
        return OpenPlatformCredentialResponse.from(credential);
    }

    @Transactional
    public OpenPlatformCredentialResponse updateCredential(UUID credentialId, SaveOpenPlatformCredentialRequest request) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        OpenPlatformCredentialEntity credential = loadOwnedCredential(currentUser, credentialId);
        assertUnique(currentUser, credentialId, request.appId(), request.apiKey());

        credential.setDisplayName(trimToNull(request.displayName()));
        credential.setAppId(request.appId().trim());
        credential.setApiKey(request.apiKey().trim());
        credential.setApiSecret(HashUtils.sha256Hex(request.apiSecret().trim()));
        credential.setUpdatedAt(OffsetDateTime.now());
        credentialMapper.updateById(credential);
        return OpenPlatformCredentialResponse.from(credential);
    }

    @Transactional
    public void deleteCredential(UUID credentialId) {
        CurrentUser currentUser = SecurityUtils.currentUser();
        OpenPlatformCredentialEntity credential = loadOwnedCredential(currentUser, credentialId);
        credentialMapper.deleteById(credential.getId());
    }

    private OpenPlatformCredentialEntity loadOwnedCredential(CurrentUser currentUser, UUID credentialId) {
        OpenPlatformCredentialEntity credential = credentialMapper.selectById(credentialId);
        if (credential == null
            || !currentUser.tenantId().equals(credential.getTenantId())
            || !currentUser.operatorId().equals(credential.getOperatorId())) {
            throw BusinessException.notFound("开放平台凭证不存在");
        }
        return credential;
    }

    private void assertUnique(CurrentUser currentUser, UUID credentialId, String appId, String apiKey) {
        LambdaQueryWrapper<OpenPlatformCredentialEntity> wrapper = baseWrapper(currentUser)
            .eq(OpenPlatformCredentialEntity::getAppId, appId.trim())
            .eq(OpenPlatformCredentialEntity::getApiKey, apiKey.trim());
        if (credentialId != null) {
            wrapper.ne(OpenPlatformCredentialEntity::getId, credentialId);
        }
        Long count = credentialMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw BusinessException.conflict("当前用户已绑定相同的 appid 和 apikey");
        }
    }

    private LambdaQueryWrapper<OpenPlatformCredentialEntity> baseWrapper(CurrentUser currentUser) {
        return new LambdaQueryWrapper<OpenPlatformCredentialEntity>()
            .eq(OpenPlatformCredentialEntity::getTenantId, currentUser.tenantId())
            .eq(OpenPlatformCredentialEntity::getOperatorId, currentUser.operatorId());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
