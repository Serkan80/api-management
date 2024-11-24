package nl.probot.api.management.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import static jakarta.persistence.CascadeType.MERGE;
import static jakarta.persistence.CascadeType.PERSIST;

@Entity
@Table(name = "subscription")
public class SubscriptionEntity extends PanacheEntity {

    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    @NotBlank
    @Column(unique = true)
    public String subscriptionKey;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    public String subject;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    public boolean enabled = true;

    @OneToMany(mappedBy = "subscription")
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

    public ApiCredentialEntity findApiCredential(Long apiId) {
        return this.apiCredentials.stream()
                .filter(api -> api.id.apiId == apiId)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No credential found for the given Api Id"));
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
        result.subscriptionKey = createSubscriptionKey();
        result.createdAt = OffsetDateTime.now(ZoneId.of("Europe/Amsterdam"));
        return result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.subject);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof SubscriptionEntity sub) {
            return Objects.equals(this.subject, sub.subject);
        }
        return false;
    }

    private static String createSubscriptionKey() {
        var length = 32;
        var rnd = new Random();
        var result = new StringBuffer(length);

        for (int i = 0; i < length; i++) {
            result.append(CHARS.charAt(rnd.nextInt(CHARS.length())));
        }
        return result.toString();
    }
}
