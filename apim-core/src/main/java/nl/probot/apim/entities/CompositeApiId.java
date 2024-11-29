package nl.probot.apim.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;

import static jakarta.persistence.FetchType.LAZY;

@Embeddable
public class CompositeApiId implements Serializable {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "api_id", updatable = false, insertable = false)
    public ApiEntity api;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "sub_id", updatable = false, insertable = false)
    public SubscriptionEntity subscription;
}
