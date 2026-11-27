package com.verifi.portal.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "organizations")
public class Organization {
    @Id
    private UUID id;
    @Column(nullable = false, length = 160)
    private String name;

    protected Organization() {}

    public Organization(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
}
