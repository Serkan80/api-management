package nl.probot.apim.core.rest;

import io.quarkus.logging.Log;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.entities.AccessListEntity;
import nl.probot.apim.core.rest.dto.AccessList;
import nl.probot.apim.core.rest.dto.AccessListPOST;
import nl.probot.apim.core.rest.dto.AccessListPUT;
import nl.probot.apim.core.rest.openapi.AccessListOpenApi;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;

import static java.util.Objects.requireNonNullElse;
import static org.hibernate.jpa.QueryHints.HINT_READONLY;
import static org.jboss.resteasy.reactive.RestResponse.created;
import static org.jboss.resteasy.reactive.RestResponse.noContent;
import static org.jboss.resteasy.reactive.RestResponse.ok;

@ApplicationScoped
@RolesAllowed("${apim.roles.manager}")
public class AccessListController implements AccessListOpenApi {

    @Override
    @Transactional
    public RestResponse<Void> save(AccessListPOST dto, SecurityContext identity, UriInfo uriInfo) {
        var entity = dto.toEntity(identity.getUserPrincipal().getName());
        entity.persist();
        Log.infof("AccessList(ip=%s, blacklisted=%s, whitelisted=%s) created", entity.ip, entity.blacklisted, entity.whitelisted);

        return created(URI.create("%s/%s".formatted(uriInfo.getPath(), entity.id)));
    }

    @Override
    @Transactional
    public RestResponse<Void> update(AccessListPUT dto, SecurityContext identity) {
        var count = AccessListEntity.updateConditionally(dto, identity.getUserPrincipal().getName());
        if (count > 0) {
            Log.infof("AccessList(ip=%s) updated", requireNonNullElse(dto.newIp(), dto.ip()));
            return ok();
        }
        return noContent();
    }

    @Override
    public AccessList findByIp(String ip) {
        return AccessListEntity.find("ip = ?1", ip)
                .project(AccessList.class)
                .singleResultOptional()
                .orElseThrow(() -> new NotFoundException("AccessList(ip=%s) not found".formatted(ip)));
    }

    @Override
    public List<AccessList> search(String searchQuery) {
        return AccessListEntity.find("ip like concat('%', ?1, '%')", Sort.ascending("ip"), searchQuery)
                .withHint(HINT_READONLY, true)
                .project(AccessList.class)
                .page(0, 50)
                .list();
    }

    @Override
    public List<AccessList> findAll() {
        return AccessListEntity.findAll(Sort.ascending("ip"))
                .withHint(HINT_READONLY, true)
                .project(AccessList.class)
                .page(0, 50)
                .list();
    }

    @Override
    @Transactional
    public RestResponse<Void> delete(String ip, SecurityContext identity) {
        var count = AccessListEntity.delete("ip = ?1", ip);
        if (count > 0) {
            Log.infof("AccessList(ip=%s) deleted by %s*****", ip, identity.getUserPrincipal().getName().substring(0, 3));
        }
        return ok();
    }
}
