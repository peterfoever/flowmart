package com.flowmart.product.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmart.product.dto.SpecDTO;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 序列化/反序列化 List<SpecDTO>
 */
@MappedTypes(List.class)
public class SpecListTypeHandler extends BaseTypeHandler<List<SpecDTO>> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<SpecDTO> parameter, JdbcType jdbcType) throws SQLException {
        try {
            ps.setString(i,MAPPER.writeValueAsString(parameter));
        } catch (JsonProcessingException e) {
            throw new SQLException("序列化 List<String> 失败", e);
        }
    }

    @Override
    public List<SpecDTO> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return List.of();
    }

    @Override
    public List<SpecDTO> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return List.of();
    }

    @Override
    public List<SpecDTO> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return List.of();
    }
}
