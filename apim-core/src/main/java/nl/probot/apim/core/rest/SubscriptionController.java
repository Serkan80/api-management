package nl.probot.apim.core.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiCredentialPUT;
import nl.probot.apim.core.rest.dto.Subscription;
import nl.probot.apim.core.rest.dto.SubscriptionAll;
import nl.probot.apim.core.rest.dto.SubscriptionApi;
import nl.probot.apim.core.rest.dto.SubscriptionPOST;
import nl.probot.apim.core.rest.dto.SubscriptionPUT;
import nl.probot.apim.core.rest.dto.Views;
import nl.probot.apim.core.rest.openapi.SubscriptionOpenApi;
import nl.probot.apim.core.utils.CacheManager;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;
import static org.jboss.resteasy.reactive.RestResponse.Status.BAD_REQUEST;

@ApplicationScoped
public class SubscriptionController implements SubscriptionOpenApi {

    @Inject
    CacheManager cacheManager;

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Map<String, String>> save(SubscriptionPOST sub, UriInfo uriInfo) {
        var entity = sub.toEntity();
        if (SubscriptionEntity.accountNotExists(entity.accounts)) {
            entity.persist();
            Log.infof("Subscription(name=%s, endDate=%s) created", entity.name, Objects.requireNonNullElse(entity.endDate, "-"));

            return RestResponse.created(URI.create("%s/%s".formatted(uriInfo.getPath(), entity.subscriptionKey)));
        }

        return RestResponse.status(BAD_REQUEST, Map.of("message", "Subscription contains account(s) that exists in another subscription"));
    }

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Void> update(String key, SubscriptionPUT sub) {
        var count = SubscriptionEntity.updateConditionally(key, sub);
        if (count > 0) {
            Log.infof("Subscription(key=%s*****, %s)", key.substring(0, 3), sub);
            return RestResponse.ok();
        }

        return RestResponse.noContent();
    }

    @Override
    @RolesAllowed("${apim.roles.manager}")
    public List<Subscription> findAll() {
        return SubscriptionEntity.findAll(Sort.descending("id"))
                .withHint(HINT_READONLY, true)
                .project(Subscription.class)
                .page(0, 50)
                .list();
    }

    @Override
    @JsonView(Views.PublicFields.class)
    @RolesAllowed({"${apim.roles.viewer}", "${apim.roles.manager}"})
    public SubscriptionAll findByKey(String key) {
        return SubscriptionAll.toDto(SubscriptionEntity.findByKey(key));
    }

    @Override
    @JsonView(Views.PublicFields.class)
    @RolesAllowed({"${apim.roles.viewer}", "${apim.roles.manager}"})
    public SubscriptionAll findByAccount(SecurityIdentity identity) {
        return SubscriptionAll.toDto(SubscriptionEntity.findActiveByAccount(identity.getPrincipal().getName()));
    }

    @Override
    @JsonView(Views.PublicFields.class)
    @RolesAllowed("${apim.roles.manager}")
    public List<Subscription> search(String searchQuery) {
        return SubscriptionEntity.search(searchQuery);
    }

    @Override
    @Transactional
    @JsonView(Views.PublicFields.class)
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<SubscriptionAll> addApi(String key, Set<Long> apiIds) {
        var apis = ApiEntity.findByIds(apiIds);

        if (!apis.isEmpty()) {
            var sub = SubscriptionEntity.findActiveByKey(key);
            apis.forEach(api -> sub.addApi(api));
            this.cacheManager.invalidate(key);

            Log.infof("New Api's for Subscription(name=%s) added", sub.name);
            return RestResponse.ok(SubscriptionAll.toDto(sub));
        } else {
            Log.warn("No Api's found for the given ids");
            return RestResponse.noContent();
        }
    }

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Void> removeApi(String key, Long apiId) {
        var deleted = SubscriptionEntity.removeApis(key, apiId);
        if (deleted > 0) {
            this.cacheManager.invalidate(key);
            Log.infof("Deleted %d apis from Subscription(apiId=%d)", deleted, apiId);
        }
        return RestResponse.ok();
    }

    @Override
    @Transactional
    @RolesAllowed({"${apim.roles.manager}"})
    public RestResponse<Void> addCredential(ApiCredential credential) {
        SubscriptionEntity.addCredential(credential);
        this.cacheManager.invalidate(credential.subscriptionKey());
        return RestResponse.ok();
    }

    @Override
    @Transactional
    @RolesAllowed({"${apim.roles.manager}"})
    public RestResponse<Void> updateCredential(ApiCredentialPUT credential) {
        var sub = SubscriptionEntity.getByNaturalId(credential.subscriptionKey());
        var apiId = credential.apiId();

        var count = SubscriptionEntity.updateCredentialConditionally(sub.id, apiId, credential);
        if (count > 0) {
            this.cacheManager.invalidate(credential.subscriptionKey());
            Log.infof("ApiCredential(apiId=%d, sub='%s') updated with %d record(s)", apiId, sub.name, count);
            return RestResponse.ok();
        }
        return RestResponse.noContent();
    }

    @Override
    @RolesAllowed({"${apim.roles.viewer}", "${apim.roles.manager}"})
    public List<SubscriptionApi> findSubscriptionForApis(Optional<String> owner) {
        return SubscriptionEntity.findSubscriptionsForApis(owner);
    }

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Map<String, Long>> cleanupExpiredSubscriptions() {
        var counts = SubscriptionEntity.cleanup();
        return RestResponse.ok(counts);
    }
}
