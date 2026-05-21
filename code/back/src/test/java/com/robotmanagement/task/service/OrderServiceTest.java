package com.robotmanagement.task.service;

import com.robotmanagement.common.exception.BusinessException;
import com.robotmanagement.common.security.CurrentUser;
import com.robotmanagement.mqtt.MqttClientService;
import com.robotmanagement.operator.mapper.OperatorRobotAccessMapper;
import com.robotmanagement.robot.entity.RobotEntity;
import com.robotmanagement.robot.entity.RobotFactsheetEntity;
import com.robotmanagement.robot.mapper.RobotFactsheetMapper;
import com.robotmanagement.robot.mapper.RobotMapper;
import com.robotmanagement.task.dto.CreateOrderRequest;
import com.robotmanagement.task.dto.InstantActionRequest;
import com.robotmanagement.task.dto.TaskNodeRequest;
import com.robotmanagement.task.entity.OrderEntity;
import com.robotmanagement.task.mapper.OrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private final OrderMapper orderMapper = mock(OrderMapper.class);
    private final RobotMapper robotMapper = mock(RobotMapper.class);
    private final OperatorRobotAccessMapper accessMapper = mock(OperatorRobotAccessMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final MqttClientService mqttClientService = mock(MqttClientService.class);
    private final RobotFactsheetMapper factsheetMapper = mock(RobotFactsheetMapper.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);

    private final UUID tenantId = UUID.randomUUID();
    private final UUID operatorId = UUID.randomUUID();
    private final UUID robotId = UUID.randomUUID();

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = newOrderService(true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new CurrentUser(operatorId, tenantId, "admin", "admin", "姒涙顓荤粔鐔稿煕", true),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            )
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("robot:header:" + robotId)).thenReturn(42L);
        when(robotMapper.selectById(robotId)).thenReturn(activeRobot());
        when(redisTemplate.hasKey("robot:auth-session:JSYS:UNIT-SN-01")).thenReturn(true);
        when(factsheetMapper.selectById(robotId)).thenReturn(factsheet(2.0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createOrderRejectsOfflineRobot() {
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(robotId, request()))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void createOrderPublishesMqttAndStoresOrder() {
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);
        ArgumentCaptor<OrderEntity> orderCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        orderService.createOrder(robotId, request());

        verify(mqttClientService).publishJson(eq("uagv/v3/JSYS/UNIT-SN-01/order"), payloadCaptor.capture(), eq(1));
        verify(orderMapper).insert(orderCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("headerId", 42L);
        assertThat(payload).containsEntry("serialNumber", "UNIT-SN-01");
        assertThat((List<?>) payload.get("nodes")).hasSize(2);
        assertThat((List<?>) payload.get("edges")).hasSize(1);
        Map<String, Object> firstNode = (Map<String, Object>) ((List<?>) payload.get("nodes")).getFirst();
        Map<String, Object> nodePosition = (Map<String, Object>) firstNode.get("nodePosition");
        Map<String, Object> firstEdge = (Map<String, Object>) ((List<?>) payload.get("edges")).getFirst();
        assertThat(nodePosition).containsEntry("mapId", "map-001");
        assertThat(firstEdge).containsEntry("maximumSpeed", 1.2);

        OrderEntity order = orderCaptor.getValue();
        assertThat(order.getTenantId()).isEqualTo(tenantId);
        assertThat(order.getRobotId()).isEqualTo(robotId);
        assertThat(order.getCreatedBy()).isEqualTo(operatorId);
        assertThat(order.getStatus()).isEqualTo("pending");
        assertThat(order.getPayload()).isEqualTo(payload);
    }

    @Test
    void createOrderRejectsSpeedBeyondFactsheetCapability() {
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);

        assertThatThrownBy(() -> orderService.createOrder(robotId, request(3.0)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void createInstantActionRejectsActionMissingFromFactsheet() {
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);

        assertThatThrownBy(() -> orderService.createInstantAction(robotId, new InstantActionRequest("stopPause", null)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void createInstantActionAllowsCancelOrderWithoutFactsheetCapability() {
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);
        when(factsheetMapper.selectById(robotId)).thenReturn(null);

        orderService.createInstantAction(robotId, new InstantActionRequest("cancelOrder", "order-001"));

        verify(mqttClientService).publishJson(eq("uagv/v3/JSYS/UNIT-SN-01/instantActions"), any(), eq(1));
    }

    @Test
    void createInstantActionAllowsFactsheetRequestWithoutFactsheet() {
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);
        when(factsheetMapper.selectById(robotId)).thenReturn(null);
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        orderService.createInstantAction(robotId, new InstantActionRequest("factsheetRequest", null));

        verify(mqttClientService).publishJson(eq("uagv/v3/JSYS/UNIT-SN-01/instantActions"), payloadCaptor.capture(), eq(1));
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("headerId", 42L);
        assertThat((List<?>) payload.get("actions")).hasSize(1);
    }

    @Test
    void createInstantActionSkipsFactsheetCapabilityWhenDisabled() {
        orderService = newOrderService(false);
        when(redisTemplate.hasKey("robot:" + robotId + ":online")).thenReturn(true);
        when(factsheetMapper.selectById(robotId)).thenReturn(null);

        orderService.createInstantAction(robotId, new InstantActionRequest("cancelOrder", "order-001"));

        verify(mqttClientService).publishJson(eq("uagv/v3/JSYS/UNIT-SN-01/instantActions"), any(), eq(1));
    }

    private OrderService newOrderService(boolean requireFactsheetCapabilities) {
        return new OrderService(
            orderMapper,
            robotMapper,
            accessMapper,
            redisTemplate,
            mqttClientService,
            factsheetMapper,
            requireFactsheetCapabilities
        );
    }

    private RobotEntity activeRobot() {
        RobotEntity robot = new RobotEntity();
        robot.setId(robotId);
        robot.setTenantId(tenantId);
        robot.setExternalRobotId("AUTH-UNIT-1001");
        robot.setSerialNumber("UNIT-SN-01");
        robot.setManufacturer("JSYS");
        robot.setStatus("active");
        return robot;
    }

    private CreateOrderRequest request() {
        return request(1.2);
    }

    private CreateOrderRequest request(double maximumSpeed) {
        return new CreateOrderRequest(
            null,
            null,
            "瀹糕剝顥呮禒璇插",
            maximumSpeed,
            List.of(
                new TaskNodeRequest("node-001", 1.2, 2.4, 0.0, "map-001", null, null, null),
                new TaskNodeRequest("node-002", 5.6, 2.8, 1.57, "map-001", null, null, null)
            ),
            null
        );
    }

    private RobotFactsheetEntity factsheet(double maximumSpeed) {
        RobotFactsheetEntity factsheet = new RobotFactsheetEntity();
        factsheet.setRobotId(robotId);
        factsheet.setTenantId(tenantId);
        factsheet.setRawPayload(Map.of(
            "physicalParameters", Map.of("maximumSpeed", maximumSpeed)
        ));
        return factsheet;
    }
}
