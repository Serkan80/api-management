package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import nl.probot.apim.commons.jpa.EncryptionConverter;

@Entity
@Table(name = "api_credential", uniqueConstraints = @UniqueConstraint(name = "sub_api_unique", columnNames = {"sub_id", "api_id"}))
public class ApiCredentialEntity extends PanacheEntityBase {

    @EmbeddedId
    public CompositeApiId id;

    @Convert(converter = EncryptionConverter.class)
    public String username;

    @Convert(converter = EncryptionConverter.class)
    public String password;

    @Convert(converter = EncryptionConverter.class)
    public String clientId;

    @Convert(converter = EncryptionConverter.class)
    public String clientSecret;
    public String clientUrl;
    public String clientScope;

    @Convert(converter = EncryptionConverter.class)
    public String apiKey;
    public String apiKeyHeader;
    public ApiKeyLocation apiKeyLocation;
}
