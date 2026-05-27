package com.autoarchive.retention;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class RetentionPolicyRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RetentionPolicyRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public List<RetentionPolicy> findActivePolicies() {
        return jdbcTemplate.query(
                """
                SELECT id, name, region, is_active, rules::text AS rules_json
                FROM retention_policies
                WHERE is_active = TRUE
                ORDER BY created_at ASC
                """,
                (rs, rowNum) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    String region = rs.getString("region");
                    boolean active = rs.getBoolean("is_active");
                    String rulesJson = rs.getString("rules_json");
                    RetentionRule rule;
                    try {
                        rule = objectMapper.readValue(rulesJson, RetentionRule.class);
                    } catch (JsonProcessingException ex) {
                        throw new IllegalStateException("Invalid retention policy JSON for id=" + id, ex);
                    }
                    return new RetentionPolicy(id, name, region, active, rule);
                });
    }
}

