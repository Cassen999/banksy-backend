package org.example.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "plaid_environment_config")
public class PlaidEnvironmentConfig {

    @Id
    private Integer id;

    @Column(name = "env", nullable = false, length = 20)
    private String env;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public Integer getId() { return id; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }
}
