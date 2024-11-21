package nl.probot.api.management.rest;

import jakarta.inject.Inject;
import nl.probot.api.management.entities.ApiEntity;
import nl.probot.api.management.entities.SubscriptionEntity;
import nl.probot.api.management.rest.dto.Subscription;
import nl.probot.api.management.rest.dto.SubscriptionAll;
import nl.probot.api.management.rest.dto.SubscriptionPOST;
import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.api.management.utils.CacheManager;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Authenticated
@Path("/subscriptions")
@SecurityScheme(type = HTTP, scheme = "basic")
public class SubscriptionController {

    @Inject
    CacheManager cacheManager;

    @POST
    @Transactional
    public RestResponse<Void> save(@Valid SubscriptionPOST sub, @Context UriInfo uriInfo) {
        var entity = SubscriptionEntity.toEntity(sub.subject());
        entity.persist();
        Log.infof("Subscription(subject=%s) created", entity.subject);

        return RestResponse.created(URI.create("%s%s".formatted(uriInfo.getPath(), entity.subscriptionKey)));
    }

    @GET
    public List<Subscription> findAll() {
        return SubscriptionEntity.findAll()
                .withHint(HINT_READONLY, true)
                .project(Subscription.class)
                .list();
    }

    @GET
    @Path("/{key}")
    public SubscriptionAll findByKey(@RestPath String key) {
        return SubscriptionAll.toDto(SubscriptionEntity.findByKey(key));
    }

    @POST
    @Path("/{key}/apis")
    @Transactional
    public RestResponse<SubscriptionAll> addApi(@RestPath String key, @NotEmpty Set<Long> apiIds) {
        var sub = SubscriptionEntity.findByKey(key);
        var apis = ApiEntity.findByIds(apiIds);

        if (!apis.isEmpty()) {
            apis.forEach(api -> sub.addApi(api));
            this.cacheManager.update(key, sub);
            Log.infof("New Api's for Subscription(subject=%s) added", sub.subject);
            return RestResponse.ok(SubscriptionAll.toDto(sub));
        } else {
            Log.warn("No Api's found for the given ids");
            return RestResponse.noContent();
        }
    }
}
