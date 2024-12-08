package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static jakarta.persistence.EnumType.STRING;
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
    @Size(max = 100)
    public String owner;

    @URL
    @Column(name = "openapi_url")
    public String openApiUrl;

    @NotBlank
    @Size(max = 200)
    public String description;

    public boolean enabled = true;

    @Enumerated(STRING)
    @Column(name = "authentication_type")
    public AuthenticationType authenticationType;

    @Min(1)
    @Max(1_000_000)
    @Column(name = "max_requests")
    public Integer maxRequests;

    @ManyToMany(mappedBy = "apis")
    public Set<SubscriptionEntity> subscriptions = new HashSet<>();

    public static List<ApiEntity> findByIds(Set<Long> apis) {
        if (apis.isEmpty()) {
            return List.of();
        }

        return find("id in (?1)", apis).withHint(HINT_READONLY, true).list();
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
