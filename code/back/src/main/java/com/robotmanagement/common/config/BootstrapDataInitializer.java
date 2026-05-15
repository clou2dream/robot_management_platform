package com.robotmanagement.common.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.robotmanagement.operator.entity.OperatorEntity;
import com.robotmanagement.operator.mapper.OperatorMapper;
import com.robotmanagement.robot.dto.RobotSyncRequest;
import com.robotmanagement.robot.service.RobotService;
import com.robotmanagement.tenant.entity.TenantEntity;
import com.robotmanagement.tenant.mapper.TenantMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Component
public class BootstrapDataInitializer implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "admin123";

    private final TenantMapper tenantMapper;
    private final OperatorMapper operatorMapper;
    private final PasswordEncoder passwordEncoder;
    private final RobotService robotService;

    public BootstrapDataInitializer(
        TenantMapper tenantMapper,
        OperatorMapper operatorMapper,
        PasswordEncoder passwordEncoder,
        RobotService robotService
    ) {
        this.tenantMapper = tenantMapper;
        this.operatorMapper = operatorMapper;
        this.passwordEncoder = passwordEncoder;
        this.robotService = robotService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (tenantMapper.selectCount(null) > 0) {
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
        admin.setPasswordResetRequired(false);
        admin.setCreatedAt(now);
        operatorMapper.insert(admin);

        seedDemoRobots(admin, tenant);
    }

    private void seedDemoRobots(OperatorEntity admin, TenantEntity tenant) {
        var authentication = new UsernamePasswordAuthenticationToken(
            new com.robotmanagement.common.security.CurrentUser(
                admin.getId(),
                tenant.getId(),
                admin.getUsername(),
                admin.getRole(),
                tenant.getName()
            ),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        try {
            robotService.syncRobots(new RobotSyncRequest(List.of()));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
