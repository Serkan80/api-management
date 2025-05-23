package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.DynamicStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiPUT;
import org.hibernate.validator.constraints.URL;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static jakarta.persistence.EnumType.STRING;
import static nl.probot.apim.commons.jpa.QueryOperator.OR;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Entity
@Table(name = "api")
public class ApiEntity extends PanacheEntity {

    @NotBlank
    @Size(max = 100)
    @Column(name = "proxy_path", unique = true)
    public String proxyPath;

    @URL
    @NotBlank
    @Column(name = "proxy_url")
    public String proxyUrl;

    @NotBlank
    @Size(max = 50)
    public String owner;

    @URL
    @Column(name = "openapi_url")
    public String openApiUrl;

    @NotBlank
    @Size(max = 100)
    public String description;

    public boolean enabled = true;

    @Enumerated(STRING)
    @Column(name = "authentication_type")
    public AuthenticationType authenticationType;

    @Min(1)
    @Max(1_000_000)
    @Column(name = "max_requests")
    public Integer maxRequests;

    @Column(name = "caching_enabled")
    public Boolean cachingEnabled = false;

    @Min(1)
    @Max(3600)
    @Column(name = "caching_ttl")
    public Integer cachingTTL;

    @Size(max = 255)
    @Column(name = "cached_paths")
    public String cachedPaths;

    @ManyToMany(mappedBy = "apis")
    public Set<SubscriptionEntity> subscriptions = new HashSet<>();

    public boolean isPathCached(String incomingRequest) {
        if (Boolean.FALSE.equals(this.cachingEnabled) || this.cachingTTL == null) {
            return false;
        }

        // just cache the incoming request
        if (isNullOrEmpty(this.cachedPaths)) {
            return true;
        }

        return Arrays.stream(this.cachedPaths.split(",")).anyMatch(path -> incomingRequest.contains(path));
    }

    public static List<ApiEntity> findByIds(Set<Long> apis) {
        if (apis.isEmpty()) {
            return List.of();
        }

        return find("id in (?1)", apis).withHint(HINT_READONLY, true).list();
    }

    public static List<Api> search(String searchQuery) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new DynamicStatement("lower(proxyPath) like concat('%', lower(:pp), '%')", searchQuery),
                new DynamicStatement("lower(proxyUrl) like concat('%', lower(:pu), '%')", searchQuery),
                new DynamicStatement("lower(owner) like concat('%', lower(:owner), '%')", searchQuery)
        ).buildWhereStatement(OR);

        return find(query, Sort.descending("id"), helper.values())
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .page(0, 50)
                .list();
    }

    public static int updateConditionally(Long apiId, ApiPUT api) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper
                .allowBlankValues()
                .statements(
                        new StaticStatement("proxyPath", api.proxyPath()),
                        new StaticStatement("proxyUrl", api.proxyUrl()),
                        new StaticStatement("owner", api.owner()),
                        new StaticStatement("openApiUrl", api.openApiUrl()),
                        new StaticStatement("description", api.description()),
                        new StaticStatement("maxRequests", api.maxRequests()),
                        new StaticStatement("enabled", api.enabled()),
                        new StaticStatement("cachingEnabled", api.cachingEnabled()),
                        new StaticStatement("cachingTTL", api.cachingTTL()),
                        new StaticStatement("cachedPaths", api.cachedPaths()),
                        new StaticStatement("authenticationType", api.authenticationType())
                ).buildUpdateStatement(new WhereStatement("id = :id", apiId));

        return update(query, helper.values());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.owner, this.proxyPath, this.proxyUrl);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof ApiEntity api) {
            return Objects.equals(this.owner, api.owner)
                    && Objects.equals(this.proxyPath, api.proxyPath)
                    && Objects.equals(this.proxyUrl, api.proxyUrl);
        }
        return false;
    }
}
