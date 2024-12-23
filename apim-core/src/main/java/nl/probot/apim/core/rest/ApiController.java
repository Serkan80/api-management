package nl.probot.apim.core.rest;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.DynamicStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.ApiPUT;
import nl.probot.apim.core.rest.openapi.ApiOpenApi;
import nl.probot.apim.core.utils.CacheManager;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

import static nl.probot.apim.commons.jpa.QuerySeparator.OR;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@ApplicationScoped
public class ApiController implements ApiOpenApi {

    @Inject
    CacheManager cacheManager;

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
    public RestResponse<Void> save(ApiPOST api, UriInfo uriInfo) {
        var apiEntity = api.toEntity();
        apiEntity.persist();
        Log.infof("Api(id=%d, proxyPath=%s, proxyUrl=%s, owner=%s) created", apiEntity.id, api.proxyPath(), api.proxyUrl(), api.owner());

        return RestResponse.created(URI.create("%s/%s".formatted(uriInfo.getPath(), apiEntity.id)));
    }

    @Override
    @Transactional
    @RolesAllowed("${apim.roles.manager}")
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
    @RolesAllowed({"${apim.roles.manager}", "${apim.roles.viewer}"})
    public List<Api> findAll() {
        return ApiEntity.findAll(Sort.descending("id"))
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .list();
    }

    @Override
    @RolesAllowed({"${apim.roles.manager}", "${apim.roles.viewer}"})
    public List<Api> search(String searchQuery) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new DynamicStatement("lower(proxyPath) like concat('%', lower(:pp), '%')", searchQuery),
                new DynamicStatement("lower(proxyUrl) like concat('%', lower(:pu), '%')", searchQuery),
                new DynamicStatement("lower(owner) like concat('%', lower(:owner), '%')", searchQuery)
        ).buildWhereStatement(OR);

        return ApiEntity.find(query, helper.values())
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .list();
    }

    @Override
    @RolesAllowed({"${apim.roles.manager}", "${apim.roles.viewer}"})
    public Api findById(Long id) {
        return ApiEntity.find("id", id)
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .singleResultOptional()
                .orElseThrow(() -> new NotFoundException("Api(id=%d) not found".formatted(id)));

    }
}
