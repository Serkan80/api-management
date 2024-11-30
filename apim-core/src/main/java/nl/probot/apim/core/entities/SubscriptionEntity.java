package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static jakarta.persistence.CascadeType.MERGE;
import static jakarta.persistence.CascadeType.PERSIST;
import static nl.probot.apim.commons.crypto.CryptoUtil.createRandomKey;

@Entity
@Table(name = "subscription")
public class SubscriptionEntity extends PanacheEntity {

    @NotBlank
    @NaturalId
    @Column(name = "subscription_key", unique = true)
    public String subscriptionKey;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    public String subject;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    public boolean enabled = true;

    @OneToMany(mappedBy = "id.subscription")
    public List<ApiCredentialEntity> apiCredentials;

    @ManyToMany(cascade = {MERGE, PERSIST})
    public Set<ApiEntity> apis = new HashSet<>();

    public void addApi(ApiEntity api) {
        this.apis.add(api);
        api.subscriptions.add(this);
    }

    public <T> T findApiBy(String proxyPath, Function<ApiEntity, T> mapper) {
        return this.apis.stream()
                .filter(api -> api.enabled)
                .filter(api -> api.proxyPath.equals(proxyPath))
                .map(mapper::apply)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Api(proxyPath=%s) not found or was not enabled".formatted(proxyPath)));
    }

    public Optional<ApiCredentialEntity> findApiCredential(Long apiId) {
        return this.apiCredentials.stream()
                .filter(credential -> credential.id.api.id.equals(apiId))
                .findFirst();
    }

    public static SubscriptionEntity getByNaturalId(String subscriptionKey) {
        return SubscriptionEntity.getEntityManager().unwrap(Session.class)
                .bySimpleNaturalId(SubscriptionEntity.class)
                .load(subscriptionKey);
    }

    public static SubscriptionEntity findByKey(String key) {
        return find("""
                select s 
                from SubscriptionEntity s 
                left join fetch s.apis a
                left join fetch s.apiCredentials ac 
                where subscriptionKey = ?1 and s.enabled = true
                """, key)
                .<SubscriptionEntity>singleResultOptional()
                .orElseThrow(() -> new NotFoundException("Subscription with given key not found"));
    }

    public static SubscriptionEntity toEntity(String subject) {
        var result = new SubscriptionEntity();
        result.subject = subject;
        result.enabled = true;
        result.subscriptionKey = createRandomKey(32);
        result.createdAt = OffsetDateTime.now(ZoneId.of("Europe/Amsterdam"));
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.subscriptionKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof SubscriptionEntity sub) {
            return Objects.equals(this.subscriptionKey, sub.subscriptionKey);
        }
        return false;
    }
}