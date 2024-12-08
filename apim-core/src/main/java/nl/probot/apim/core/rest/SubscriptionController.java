package nl.probot.apim.core.rest;

import com.fasterxml.jackson.annotation.JsonView;
import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.rest.dto.Subscription;
import nl.probot.apim.core.rest.dto.SubscriptionAll;
import nl.probot.apim.core.rest.dto.SubscriptionPOST;
import nl.probot.apim.core.rest.dto.SubscriptionPUT;
import nl.probot.apim.core.rest.dto.Views;
import nl.probot.apim.core.rest.openapi.SubscriptionOpenApi;
import nl.probot.apim.core.utils.CacheManager;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@ApplicationScoped
public class SubscriptionController implements SubscriptionOpenApi {

    @Inject
    CacheManager cacheManager;

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Void> save(SubscriptionPOST sub, UriInfo uriInfo) {
        var entity = SubscriptionEntity.toEntity(sub);
        entity.persist();
        Log.infof("Subscription(name=%s, endDate=%s) created", entity.name, Objects.requireNonNullElse(entity.endDate, "unlimited"));

        return RestResponse.created(URI.create("%s/%s".formatted(uriInfo.getPath(), entity.subscriptionKey)));
    }

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Void> update(String key, SubscriptionPUT sub) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new StaticStatement("enabled", sub.enabled()),
                new StaticStatement("endDate", sub.endDate())
        ).buildUpdateStatement(new WhereStatement("subscriptionKey = :key", key));

        var count = SubscriptionEntity.update(query, helper.values());
        if (count > 0) {
            Log.infof("Subscription(key=%s*****, %s)", key.substring(0, 3), sub);
            return RestResponse.ok();
        }

        return RestResponse.noContent();
    }

    @Override
    @RolesAllowed("${apim.roles.manager}")
    public List<Subscription> findAll() {
        return SubscriptionEntity.findAll()
                .withHint(HINT_READONLY, true)
                .project(Subscription.class)
                .list();
    }

    @Override
    @JsonView(Views.PublicFields.class)
    @RolesAllowed({"${apim.roles.viewer}", "${apim.roles.manager}"})
    public SubscriptionAll findByKey(String key) {
        return SubscriptionAll.toDto(SubscriptionEntity.findByKey(key));
    }

    @Override
    @Transactional
    @JsonView(Views.PublicFields.class)
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<SubscriptionAll> addApi(String key, Set<Long> apiIds) {
        var sub = SubscriptionEntity.findByKey(key);
        var apis = ApiEntity.findByIds(apiIds);

        if (!apis.isEmpty()) {
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
    public RestResponse<Void> cleanupExpiredSubscriptions() {
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
            var count = ApiCredentialEntity.delete("id.subscription.id in (?1)", ids);
            Log.infof("%d expired credentials(s) deleted", count);

            count = SubscriptionEntity.delete("id in (?1)", ids);
            Log.infof("%d expired subscription(s) deleted", count);
        }

        return RestResponse.noContent();
    }
}
