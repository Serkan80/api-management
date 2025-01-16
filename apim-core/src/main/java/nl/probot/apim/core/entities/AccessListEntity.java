package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import io.quarkus.logging.Log;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import nl.probot.apim.core.rest.dto.AccessListPUT;
import nl.probot.apim.core.utils.IpUtility;

import java.net.UnknownHostException;
import java.time.OffsetDateTime;
import java.util.List;

import static java.util.Objects.requireNonNull;

@Entity
@Table(name = "access_list", indexes = {@Index(name = "idx_ip", columnList = "ip")})
public class AccessListEntity extends PanacheEntity {

    @NotBlank
    @Column(unique = true)
    public String ip;

    @NotBlank
    @Column(name = "updated_by")
    public String updatedBy;
    public Boolean blacklisted;
    public Boolean whitelisted;

    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(name = "is_cidr")
    public Boolean isCidr;

    @Size(min = 1, max = 100)
    public String description;

    @PrePersist
    public void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public static boolean hasAccess(String ip) {
        var exactMatch = find("ip = ?1", ip).<AccessListEntity>firstResult();
        if (exactMatch != null) {
            return isAllowed(exactMatch);
        }

        try {
            var cidrList = AccessListEntity.<AccessListEntity>list("isCidr = true");
            var cidrWhitelist = cidrList.stream().filter(entry -> entry.whitelisted).toList();
            var cidrBlacklist = cidrList.stream().filter(entry -> entry.blacklisted).toList();

            if (!cidrWhitelist.isEmpty()) {
                return isInCdrlist(ip, cidrWhitelist, false);
            }

            if (!cidrBlacklist.isEmpty()) {
                return isInCdrlist(ip, cidrBlacklist, true);
            }

            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    public static int updateConditionally(AccessListPUT dto, String user) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper
                .statements(
                        new StaticStatement("ip", dto.newIp()),
                        new StaticStatement("blacklisted", dto.blacklisted()),
                        new StaticStatement("whitelisted", dto.whitelisted()),
                        new StaticStatement("updatedBy", requireNonNull(user)),
                        new StaticStatement("description", dto.description())
                ).buildUpdateStatement(new WhereStatement("ip = :ip", dto.ip()));

        return update(query, helper.values());
    }

    private static boolean isAllowed(AccessListEntity entry) {
        if (entry.whitelisted != null && entry.whitelisted) {
            return true;
        }
        return entry.blacklisted == null || !entry.blacklisted;
    }

    private static boolean isInCdrlist(String ipAddress, List<AccessListEntity> cdrList, boolean allowAccess) throws UnknownHostException {
        for (var entry : cdrList) {
            if (IpUtility.isIPv4InRange(ipAddress, entry.ip) || IpUtility.isIPv6InRange(ipAddress, entry.ip)) {
                if (!allowAccess) {
                    Log.warnf("Blocked %s ip address detected", ipAddress);
                }
                return isAllowed(entry);
            }
        }
        return allowAccess;
    }
}
