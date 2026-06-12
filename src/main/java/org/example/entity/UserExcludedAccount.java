package org.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_excluded_accounts")
public class UserExcludedAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "plaid_account_id", nullable = false, length = 255)
    private String plaidAccountId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getPlaidAccountId() { return plaidAccountId; }
    public void setPlaidAccountId(String plaidAccountId) { this.plaidAccountId = plaidAccountId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
