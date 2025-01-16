package nl.probot.apim.core.rest;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.entities.AccessListEntity;
import nl.probot.apim.core.rest.dto.AccessList;
import nl.probot.apim.core.rest.dto.AccessListPOST;
import nl.probot.apim.core.rest.dto.AccessListPUT;
import nl.probot.apim.core.rest.openapi.AccessListOpenApi;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;
import static org.jboss.resteasy.reactive.RestResponse.created;
import static org.jboss.resteasy.reactive.RestResponse.noContent;
import static org.jboss.resteasy.reactive.RestResponse.ok;

@RolesAllowed("${apim.roles.manager}")
public class AccessListController implements AccessListOpenApi {

    @Inject
    SecurityIdentity identity;

    @Override
    @Transactional
    public RestResponse<Void> save(AccessListPOST dto, UriInfo uriInfo) {
        var entity = dto.toEntity(this.identity.getPrincipal().getName());
        entity.persist();
        Log.infof("AccessList(ip=%d, blacklisted=%s, whitelisted=%s) created", entity.ip, entity.blacklisted, entity.whitelisted);

        return created(URI.create("%s/%s".formatted(uriInfo.getPath(), entity.id)));
    }

    @Override
    @Transactional
    public RestResponse<Void> update(AccessListPUT dto) {
        var count = AccessListEntity.updateConditionally(dto, this.identity.getPrincipal().getName());
        if (count > 0) {
            Log.infof("AccessList(ip=%s) updated", dto.ip(), count);
            return ok();
        }
        return noContent();
    }

    @Override
    public List<AccessList> findAll() {
        return AccessListEntity.findAll(Sort.ascending("ip"))
                .withHint(HINT_READONLY, true)
                .project(AccessList.class)
                .list();
    }
}
