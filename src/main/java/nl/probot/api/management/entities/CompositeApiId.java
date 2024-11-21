package nl.probot.api.management.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;

@Embeddable
public class CompositeApiId implements Serializable {

    @Column(name = "api_id")
    public long apiId;

    public long subscriptionId;
}
