package org.example.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plaid_accounts")
public class PlaidAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "plaid_item_id", nullable = false)
    private PlaidItem plaidItem;

    @Column(name = "plaid_account_id", nullable = false, unique = true, length = 255)
    private String plaidAccountId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "official_name", length = 255)
    private String officialName;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "subtype", length = 50)
    private String subtype;

    @Column(name = "mask", length = 4)
    private String mask;

    @Column(name = "hidden", nullable = false)
    private boolean hidden = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public PlaidItem getPlaidItem() { return plaidItem; }
    public void setPlaidItem(PlaidItem plaidItem) { this.plaidItem = plaidItem; }
    public String getPlaidAccountId() { return plaidAccountId; }
    public void setPlaidAccountId(String plaidAccountId) { this.plaidAccountId = plaidAccountId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOfficialName() { return officialName; }
    public void setOfficialName(String officialName) { this.officialName = officialName; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getSubtype() { return subtype; }
    public void setSubtype(String subtype) { this.subtype = subtype; }
    public String getMask() { return mask; }
    public void setMask(String mask) { this.mask = mask; }
    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
