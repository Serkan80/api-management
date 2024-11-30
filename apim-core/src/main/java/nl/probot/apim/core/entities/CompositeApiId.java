package nl.probot.apim.core.entities;

import jakarta.persistence.Embeddable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import java.io.Serializable;
import java.util.Objects;

import static jakarta.persistence.FetchType.LAZY;

@Embeddable
public class CompositeApiId implements Serializable {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "api_id", updatable = false, insertable = false)
    public ApiEntity api;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "sub_id", updatable = false, insertable = false)
    public SubscriptionEntity subscription;

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj instanceof CompositeApiId id) {
            return Objects.equals(id.api.id, this.api.id)
                   && Objects.equals(id.subscription.id, this.subscription.id);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.api.id, this.subscription.id);
    }
}
