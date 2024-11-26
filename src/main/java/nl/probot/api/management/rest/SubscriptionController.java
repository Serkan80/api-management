package nl.probot.api.management.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.api.management.entities.ApiEntity;
import nl.probot.api.management.entities.SubscriptionEntity;
import nl.probot.api.management.rest.dto.Subscription;
import nl.probot.api.management.rest.dto.SubscriptionAll;
import nl.probot.api.management.rest.dto.SubscriptionPOST;
import nl.probot.api.management.rest.dto.Views;
import nl.probot.api.management.rest.openapi.SubscriptionOpenApi;
import nl.probot.api.management.utils.CacheManager;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Authenticated
@ApplicationScoped
public class SubscriptionController implements SubscriptionOpenApi {

    @Inject
    CacheManager cacheManager;

    @Override
    @Transactional
    public RestResponse<Void> save(@Valid SubscriptionPOST sub, @Context UriInfo uriInfo) {
        var entity = SubscriptionEntity.toEntity(sub.subject());
        entity.persist();
        Log.infof("Subscription(subject=%s) created", entity.subject);

        return RestResponse.created(URI.create("%s/%s".formatted(uriInfo.getPath(), entity.subscriptionKey)));
    }

    @Override
    public List<Subscription> findAll() {
        return SubscriptionEntity.findAll()
                .withHint(HINT_READONLY, true)
                .project(Subscription.class)
                .list();
    }

    @Override
    @JsonView(Views.PublicFields.class)
    public SubscriptionAll findByKey(@RestPath String key) {
        return SubscriptionAll.toDto(SubscriptionEntity.findByKey(key));
    }

    @Override
    @Transactional
    @JsonView(Views.PublicFields.class)
    public RestResponse<SubscriptionAll> addApi(@RestPath String key, @NotEmpty Set<Long> apiIds) {
        var sub = SubscriptionEntity.findByKey(key);
        var apis = ApiEntity.findByIds(apiIds);

        if (!apis.isEmpty()) {
            apis.forEach(api -> sub.addApi(api));
            this.cacheManager.invalidate(key);
            Log.infof("New Api's for Subscription(subject=%s) added", sub.subject);
            return RestResponse.ok(SubscriptionAll.toDto(sub));
        } else {
            Log.warn("No Api's found for the given ids");
            return RestResponse.noContent();
        }
    }
}
