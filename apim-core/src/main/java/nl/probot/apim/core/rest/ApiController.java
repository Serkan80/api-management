package nl.probot.apim.core.rest;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiCredentialPUT;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.ApiPUT;
import nl.probot.apim.core.rest.openapi.ApiOpenApi;
import nl.probot.apim.core.utils.CacheManager;
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
        return RestResponse.created(URI.create("%s/%s".formatted(uriInfo.getPath(), apiEntity.id)));
    }

    @Override
    @Transactional
    public RestResponse<Void> update(Long apiId, ApiPUT api) {
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
    public List<Api> findAll() {
        return ApiEntity.findAll(Sort.ascending("owner"))
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .list();
    }

    @Override
    public Api findById(Long id) {
        return ApiEntity.find("id", id)
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .singleResultOptional()
                .orElseThrow(() -> new NotFoundException("Api(id=%d) not found".formatted(id)));

    }

    @Override
    @Transactional
    public RestResponse<Void> addCredential(Long apiId, ApiCredential credential) {
        var subscriptionEntity = SubscriptionEntity.getByNaturalId(credential.subscriptionKey());
        var apiEntity = ApiEntity.getEntityManager().getReference(ApiEntity.class, apiId);
        var credentialEntity = credential.toEntity();
        credentialEntity.id.api = apiEntity;
        credentialEntity.id.subscription = subscriptionEntity;
        credentialEntity.persist();
        this.cacheManager.invalidate(credential.subscriptionKey());
        Log.infof("ApiCredential(apiId=%d, sub='%s') added", apiEntity.id, subscriptionEntity.subject);

        return RestResponse.ok();
    }

    @Override
    @Transactional
    public RestResponse<Void> updateCredential(Long apiId, ApiCredentialPUT credential) {
        var sub = SubscriptionEntity.getByNaturalId(credential.subscriptionKey());
        var subId = sub.id;

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

        var count = ApiCredentialEntity.update(query, helper.values());
        if (count > 0) {
            this.cacheManager.invalidate(credential.subscriptionKey());
            Log.infof("ApiCredential(apiId=%d, sub='%s') updated with %d record(s)", apiId, sub.subject, count);
            return RestResponse.ok();
        }
        return RestResponse.noContent();
    }
}
