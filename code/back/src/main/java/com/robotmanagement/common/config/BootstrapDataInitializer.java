package com.robotmanagement.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.tenant.entity.TenantEntity;
import com.robotmanagement.tenant.mapper.TenantMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final TenantMapper tenantMapper;
    private final OperatorMapper operatorMapper;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    public BootstrapDataInitializer(
        TenantMapper tenantMapper,
        OperatorMapper operatorMapper,
        PasswordEncoder passwordEncoder,
        Environment environment
    ) {
        this.tenantMapper = tenantMapper;
        this.operatorMapper = operatorMapper;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantMapper.selectCount(null) > 0) {
            ensureLocalDevSeed();
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        TenantEntity tenant = new TenantEntity();
        tenant.setId(UUID.randomUUID());
        tenant.setName("默认租户");
        tenant.setCreatedAt(now);
        tenantMapper.insert(tenant);

        OperatorEntity admin = new OperatorEntity();
        admin.setId(UUID.randomUUID());
        admin.setTenantId(tenant.getId());
        admin.setUsername(DEFAULT_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setRole("admin");
        admin.setDebugPermission(false);
        admin.setPasswordResetRequired(false);
        admin.setCreatedAt(now);
        operatorMapper.insert(admin);

        ensureLocalDevSeed();
    }

    private void ensureLocalDevSeed() {
        if (!environment.getProperty("app.bootstrap.local-dev-seed", Boolean.class, false)) {
            return;
        }

        TenantEntity tenant = tenantMapper.selectList(
            new LambdaQueryWrapper<TenantEntity>()
                .orderByAsc(TenantEntity::getCreatedAt)
                .last("limit 1")
        ).stream().findFirst().orElse(null);
        if (tenant == null) {
            return;
        }

        String username = environment.getProperty("app.bootstrap.local-dev-username", "local_admin");
        String password = environment.getProperty("app.bootstrap.local-dev-password", "local123");
        OffsetDateTime now = OffsetDateTime.now();

        OperatorEntity operator = operatorMapper.selectOne(
            new LambdaQueryWrapper<OperatorEntity>().eq(OperatorEntity::getUsername, username)
        );
        if (operator == null) {
            operator = new OperatorEntity();
            operator.setId(UUID.randomUUID());
            operator.setTenantId(tenant.getId());
            operator.setUsername(username);
            operator.setCreatedAt(now);
        }
        operator.setPasswordHash(passwordEncoder.encode(password));
        operator.setRole("admin");
        operator.setDebugPermission(Boolean.TRUE.equals(operator.getDebugPermission()));
        operator.setPasswordResetRequired(false);

        if (operatorMapper.selectById(operator.getId()) == null) {
            operatorMapper.insert(operator);
        } else {
            operatorMapper.updateById(operator);
        }

    }
}
