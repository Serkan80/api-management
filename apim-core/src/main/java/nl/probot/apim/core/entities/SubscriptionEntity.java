package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.DynamicStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiCredentialPUT;
import nl.probot.apim.core.rest.dto.Subscription;
import nl.probot.apim.core.rest.dto.SubscriptionPUT;
import org.hibernate.Session;
import org.hibernate.annotations.Array;
import org.hibernate.annotations.NaturalId;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static jakarta.persistence.CascadeType.MERGE;
import static jakarta.persistence.CascadeType.PERSIST;
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

    public static SubscriptionEntity getByNaturalId(String subscriptionKey) {
        return getEntityManager().unwrap(Session.class)
                .bySimpleNaturalId(SubscriptionEntity.class)
                .load(subscriptionKey);
    }

    public ApiEntity findApi(String incomingRequestPath) {
        var path = incomingRequestPath.substring(incomingRequestPath.indexOf('/', 1));
        return this.apis.stream()
                .filter(api -> api.enabled)
                .filter(api -> path.startsWith(api.proxyPath))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Api(proxyPath=%s) not found or was not enabled on current subscription".formatted(path)));
    }

    public Optional<ApiCredentialEntity> findApiCredential(Long apiId) {
        return this.apiCredentials.stream()
                .filter(credential -> credential.id.api.id.equals(apiId))
                .findFirst();
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

        return find(query, Sort.descending("id"), helper.values())
                .withHint(HINT_READONLY, true)
                .project(Subscription.class)
                .page(0, 50)
                .list();
    }

    public static boolean accountNotExists(String[] users) {
        return accountNotExists(null, users);
    }

    public static boolean accountNotExists(String subscriptionKey, String[] users) {
        var usersArray = "{%s}".formatted(String.join(",", users));
        var where = new PanacheDyanmicQueryHelper().statements(
                new DynamicStatement("subscription_key <> :subKey", subscriptionKey),
                new DynamicStatement("accounts && cast(:users as varchar[])", usersArray)
        ).buildWhereStatement();

        var query = getEntityManager().createNativeQuery("select count(*) from subscription where %s".formatted(where));

        if (subscriptionKey != null) {
            query.setParameter(1, subscriptionKey).setParameter(2, usersArray);
        } else {
            query.setParameter(1, usersArray);
        }

        return (Long) query.getSingleResult() == 0;
    }

    public static int updateCredentialConditionally(Long subId, Long apiId, ApiCredentialPUT credential) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new StaticStatement("username", credential.username()),
                new StaticStatement("password", credential.password()),
                new StaticStatement("clientId", credential.clientId()),
                new StaticStatement("clientSecret", credential.clientSecret()),
                new StaticStatement("clientUrl", credential.clientUrl()),
                new StaticStatement("clientScope", credential.clientScope()),
                new StaticStatement("apiKey", credential.apiKey()),
                new StaticStatement("apiKeyHeader", credential.apiKeyHeader()),
                new StaticStatement("apiKeyLocation", credential.apiKeyLocation())
        ).buildUpdateStatement(new WhereStatement("id.api.id = :apiId and id.subscription.id = :subId", List.of(apiId, subId)));

        return ApiCredentialEntity.update(query, helper.values());
    }

    public static int updateConditionally(String key, SubscriptionPUT sub) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new StaticStatement("enabled", sub.enabled()),
                new StaticStatement("endDate", sub.endDate()),
                new StaticStatement("accounts", sub.accountAsArray())
        ).buildUpdateStatement(new WhereStatement("subscriptionKey = :key", key));

        if (sub.accounts() != null && !sub.accounts().isEmpty()) {
            if (!SubscriptionEntity.accountNotExists(key, sub.accountAsArray())) {
                throw new WebApplicationException("Subscription contains account(s) that exists in another subscription", 400);
            }
        }

        return SubscriptionEntity.update(query, helper.values());
    }

    public static void addCredential(ApiCredential credential) {
        var apiEntity = ApiEntity.getEntityManager().getReference(ApiEntity.class, credential.apiId());
        var subscriptionEntity = Optional.ofNullable(SubscriptionEntity.getByNaturalId(credential.subscriptionKey()))
                .orElseThrow(() -> new NotFoundException("Subscription with the given key not found or was disabled"));

        var credentialEntity = credential.toEntity();
        credentialEntity.id.api = apiEntity;
        credentialEntity.id.subscription = subscriptionEntity;
        credentialEntity.persist();
        Log.infof("ApiCredential(apiId=%d, sub='%s') added", apiEntity.id, subscriptionEntity.name);
    }

    public static List<Long> cleanup() {
        //@formatter:off
        var ids = SubscriptionEntity.find("""
                                          select id
                                          from SubscriptionEntity s
                                          where s.endDate is not null and s.endDate <= current_date 
                                          """)
                .project(Long.class)
                .list();
        //@formatter:on

        if (!ids.isEmpty()) {
            var count1 = ApiCredentialEntity.delete("id.subscription.id in (?1)", ids);
            Log.infof("%d expired credentials(s) deleted", count1);

            var count2 = SubscriptionEntity.delete("id in (?1)", ids);
            Log.infof("%d expired subscription(s) deleted", count2);
        }
        return ids;
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
