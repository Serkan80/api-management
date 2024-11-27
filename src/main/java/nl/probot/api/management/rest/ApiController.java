package nl.probot.api.management.rest;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.api.management.entities.ApiCredentialEntity;
import nl.probot.api.management.entities.ApiEntity;
import nl.probot.api.management.entities.SubscriptionEntity;
import nl.probot.api.management.rest.dto.Api;
import nl.probot.api.management.rest.dto.ApiCredential;
import nl.probot.api.management.rest.dto.ApiPOST;
import nl.probot.api.management.rest.dto.ApiUPDATE;
import nl.probot.api.management.rest.openapi.ApiOpenApi;
import nl.probot.api.management.utils.CacheManager;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.api.management.utils.PanacheDyanmicQueryHelper.WhereStatement;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Authenticated
@ApplicationScoped
public class ApiController implements ApiOpenApi {

    @Inject
    CacheManager cacheManager;

    @Override
    @Transactional
    public RestResponse<Void> save(ApiPOST api, UriInfo uriInfo) {
        var apiEntity = api.toEntity();
        apiEntity.persist();
        Log.infof("Api(id=%d, proxyPath=%s, proxyUrl=%s, owner=%s) created", apiEntity.id, api.proxyPath(), api.proxyUrl(), api.owner());

        var credential = api.credential();
        if (credential != null) {
            addCredential(apiEntity.id, credential);
        }
        return RestResponse.created(URI.create(uriInfo.getPath()));
    }

    @Override
    @Transactional
    public RestResponse<Void> update(Long apiId, ApiUPDATE api) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new StaticStatement("proxyPath", api.proxyPath()),
                new StaticStatement("proxyUrl", api.proxyUrl()),
                new StaticStatement("owner", api.owner()),
                new StaticStatement("openApiUrl", api.openApiUrl()),
                new StaticStatement("description", api.description()),
                new StaticStatement("maxRequests", api.maxRequests()),
                new StaticStatement("authenticationType", api.authenticationType())
        ).buildUpdateStatement(new WhereStatement("id = :id", apiId));

        var count = ApiEntity.update(query, helper.values());
        if (count > 0) {
            // many subscribers can have this api, so just clear all for simplicity
            this.cacheManager.clearAll();
            Log.infof("Api(id=%d) updated with %d records", apiId, count);
            return RestResponse.ok();
        }
        return RestResponse.noContent();
    }

    @Override
    @Transactional
    public void addCredential(Long apiId, ApiCredential credential) {
        var subscriptionEntity = SubscriptionEntity.getByNaturalId(credential.subscriptionKey());
        var apiEntity = ApiEntity.getEntityManager().getReference(ApiEntity.class, apiId);
        var credentialEntity = credential.toEntity();
        credentialEntity.id.api = apiEntity;
        credentialEntity.id.subscription = subscriptionEntity;
        credentialEntity.persist();
        this.cacheManager.invalidate(credential.subscriptionKey());
        Log.infof("ApiCredential(apiId=%d, subKey=%s) added", apiEntity.id, credential.subscriptionKey());
    }

    @Override
    @Transactional
    public RestResponse<Void> updateCredential(Long apiId, ApiCredential credential) {
        var subId = SubscriptionEntity.getByNaturalId(credential.subscriptionKey()).id;
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
                new StaticStatement("apiKeyHeaderOutsideAuthorization", credential.apiKeyHeaderOutsideAuthorization())
        ).buildUpdateStatement(new WhereStatement("id.api.id = :apiId and id.subscription.id = :subId", List.of(apiId, subId)));

        var count = ApiCredentialEntity.update(query, helper.values());
        if (count > 0) {
            this.cacheManager.invalidate(credential.subscriptionKey());
            Log.infof("ApiCredential(apiId=%d, subKey=%s) updated with %d record(s)", apiId, credential.subscriptionKey(), count);
            return RestResponse.ok();
        }
        return RestResponse.noContent();
    }

    @Override
    public List<Api> findAll() {
        return ApiEntity.findAll(Sort.ascending("owner"))
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .list();
    }
}
