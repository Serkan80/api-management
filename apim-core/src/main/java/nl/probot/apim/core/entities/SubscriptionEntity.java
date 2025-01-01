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
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.DynamicStatement;
import nl.probot.apim.core.rest.dto.Subscription;
import nl.probot.apim.core.rest.dto.SubscriptionPOST;
import org.hibernate.Session;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.NaturalId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static jakarta.persistence.CascadeType.MERGE;
import static jakarta.persistence.CascadeType.PERSIST;
import static nl.probot.apim.commons.crypto.CryptoUtil.createRandomKey;
import static nl.probot.apim.commons.jpa.QuerySeparator.OR;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

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
    public String name;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "end_date")
    public LocalDate endDate;

    public boolean enabled = true;

    @OneToMany(mappedBy = "id.subscription")
    public List<ApiCredentialEntity> apiCredentials;

    @Array(length = 20)
    public String[] accounts;

    @ManyToMany(cascade = {MERGE, PERSIST})
    public Set<ApiEntity> apis = new HashSet<>();

    public void addApi(ApiEntity api) {
        this.apis.add(api);
        api.subscriptions.add(this);
    }

    public ApiEntity findApi(String incomingRequestPath) {
        var path = incomingRequestPath.substring(incomingRequestPath.indexOf('/', 1));
        return this.apis.stream()
                .filter(api -> api.enabled)
                .filter(api -> path.startsWith(api.proxyPath))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Api(proxyPath=%s) not found or was not enabled".formatted(path)));
    }

    public Optional<ApiCredentialEntity> findApiCredential(Long apiId) {
        return this.apiCredentials.stream()
                .filter(credential -> credential.id.api.id.equals(apiId))
                .findFirst();
    }

    public static SubscriptionEntity getByNaturalId(String subscriptionKey) {
        return getEntityManager().unwrap(Session.class)
                .bySimpleNaturalId(SubscriptionEntity.class)
                .load(subscriptionKey);
    }

    public static SubscriptionEntity findByKey(String key) {
        return find("""
                select s 
                from SubscriptionEntity s 
                left join fetch s.apis a
                left join fetch s.apiCredentials ac 
                where subscriptionKey = ?1 
                """, key)
                .<SubscriptionEntity>singleResultOptional()
                .orElseThrow(() -> new NotFoundException("Subscription with given key not found"));
    }

    public static SubscriptionEntity findActiveByKey(String key) {
        return find("""
                select s 
                from SubscriptionEntity s 
                left join fetch s.apis a
                left join fetch s.apiCredentials ac 
                where subscriptionKey = ?1 and s.enabled = true 
                and (s.endDate is null or s.endDate > current_date)
                """, key)
                .<SubscriptionEntity>singleResultOptional()
                .orElseThrow(() -> new NotFoundException("Subscription with given key not found or is inactive"));
    }

    public static SubscriptionEntity findActiveByAccount(String account) {
        return find("""
                select s 
                from SubscriptionEntity s 
                left join fetch s.apis a
                left join fetch s.apiCredentials ac 
                where array_any(accounts, ?1) 
                and s.enabled = true 
                and (s.endDate is null or s.endDate > current_date)
                """, account)
                .<SubscriptionEntity>singleResultOptional()
                .orElseThrow(() -> new NotFoundException("Subscription with given key not found or is inactive"));
    }

    public static List<Subscription> search(String searchQuery) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new DynamicStatement("lower(name) like concat('%', lower(:name), '%')", searchQuery),
                new DynamicStatement("array_any(accounts, :query)", searchQuery)
        ).buildWhereStatement(OR);

        return find(query, helper.values())
                .withHint(HINT_READONLY, true)
                .project(Subscription.class)
                .list();
    }

    public static SubscriptionEntity toEntity(SubscriptionPOST sub) {
        var result = new SubscriptionEntity();
        result.name = sub.name();
        result.enabled = true;
        result.subscriptionKey = createRandomKey(32);
        result.createdAt = OffsetDateTime.now(ZoneId.of("Europe/Amsterdam"));
        result.endDate = sub.endDate();
        result.accounts = sub.accounts();
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
