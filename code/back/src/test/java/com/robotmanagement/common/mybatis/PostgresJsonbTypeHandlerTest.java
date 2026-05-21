package com.robotmanagement.common.mybatis;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostgresJsonbTypeHandlerTest {

    private final PostgresJsonbTypeHandler typeHandler = new PostgresJsonbTypeHandler();

    @Test
    void writesJsonbAsJdbcOther() throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);

        typeHandler.setNonNullParameter(statement, 1, Map.of("batterySoc", 88), JdbcType.OTHER);

        verify(statement).setObject(eq(1), anyString(), eq(Types.OTHER));
    }

    @Test
    void readsJsonbAsMap() throws Exception {
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getString("payload")).thenReturn("{\"batterySoc\":88,\"position\":{\"x\":1.2}}");

        Map<String, Object> value = typeHandler.getNullableResult(resultSet, "payload");

        assertThat(value).containsEntry("batterySoc", 88);
        assertThat(value.get("position")).isInstanceOf(Map.class);
    }
}
