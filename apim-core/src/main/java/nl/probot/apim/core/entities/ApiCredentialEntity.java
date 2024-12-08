package nl.probot.apim.core.entities;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
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

    @Column(name = "client_id")
    @Convert(converter = EncryptionConverter.class)
    public String clientId;

    @Column(name = "client_secret")
    @Convert(converter = EncryptionConverter.class)
    public String clientSecret;

    @Column(name = "client_url")
    public String clientUrl;

    @Column(name = "client_scope")
    public String clientScope;

    @Column(name = "apikey")
    @Convert(converter = EncryptionConverter.class)
    public String apiKey;

    @Column(name = "apikey_header")
    public String apiKeyHeader;

    @Column(name = "apikey_location")
    public ApiKeyLocation apiKeyLocation;
}
