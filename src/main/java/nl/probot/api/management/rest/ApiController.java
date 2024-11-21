package nl.probot.api.management.rest;

import nl.probot.api.management.entities.ApiEntity;
import nl.probot.api.management.entities.SubscriptionEntity;
import nl.probot.api.management.rest.dto.Api;
import nl.probot.api.management.rest.dto.ApiCredential;
import nl.probot.api.management.rest.dto.ApiPOST;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Path("/apis")
@Authenticated
@SecurityScheme(type = HTTP, scheme = "basic")
public class ApiController {

    @POST
    @Transactional
    public RestResponse<Void> save(@Valid ApiPOST api, @Context UriInfo uriInfo) {
        api.toEntity().persist();
        Log.infof("Api(proxyPath=%s, proxyUrl=%s, owner=%s) created", api.proxyPath(), api.proxyUrl(), api.owner());
        return RestResponse.created(URI.create(uriInfo.getPath()));
    }

    @POST
    @Path("/{apiId}/credentials")
    @Transactional
    public RestResponse<Void> addCredential(@RestPath Long apiId, @Valid ApiCredential credential, @Context UriInfo uriInfo) {
        var subscription = SubscriptionEntity.getEntityManager().getReference(SubscriptionEntity.class, credential.subscriptionId());
        var credentialEntity = credential.toEntity();
        credentialEntity.id.apiId = apiId;
        credentialEntity.subscription = subscription;
        credentialEntity.persist();

        return RestResponse.created(uriInfo.getBaseUri());
    }

    @GET
    public List<Api> findAll() {
        return ApiEntity.findAll(Sort.ascending("owner"))
                .withHint(HINT_READONLY, true)
                .project(Api.class)
                .list();
    }
}
