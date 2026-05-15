package com.robotmanagement.robot.dto;

import com.robotmanagement.robot.entity.RobotPositionEntity;
import com.robotmanagement.robot.entity.RobotStateEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RobotPositionResponse(
    UUID robotId,
    Double x,
    Double y,
    Double theta,
    String mapId,
    OffsetDateTime time
) {

    public static RobotPositionResponse from(RobotPositionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RobotPositionResponse(
            entity.getRobotId(),
            entity.getPosX(),
            entity.getPosY(),
            entity.getPosTheta(),
            entity.getMapId(),
            entity.getTime()
        );
    }

    public static RobotPositionResponse fromState(RobotStateEntity entity) {
        if (entity == null) {
            return null;
        }
        return new RobotPositionResponse(
            entity.getRobotId(),
            entity.getPosX(),
            entity.getPosY(),
            entity.getPosTheta(),
            entity.getMapId(),
            entity.getTime()
        );
    }
}
