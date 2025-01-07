package nl.probot.apim.core.rest;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.ApiPUT;
import nl.probot.apim.core.rest.openapi.ApiOpenApi;
import nl.probot.apim.core.utils.CacheManager;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

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
        var count = ApiEntity.updateConditionally(apiId, api);
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
                .page(0, 50)
                .list();
    }

    @Override
    @RolesAllowed({"${apim.roles.manager}", "${apim.roles.viewer}"})
    public List<Api> search(String searchQuery) {
        return ApiEntity.search(searchQuery);
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
