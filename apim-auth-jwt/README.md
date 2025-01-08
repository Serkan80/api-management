# apim-auth-jwt

This module enables custom JWT authentication to the following authentication modules:

- `apim-auth-file-properties`
- `apim-auth-jpa`
- `apim-auth-ldap`

This means, that the authentication to the APIM will switch from Basic Authentication into Bearer authentication.

## Usage

First, depending on which authentication module is chosen, you need to make sure that you disable *one* of the following properties in `/apim-application/src/main/resources/application.properties`:

If `apim-auth-file-properties` is chosen, then you need to disable this:

```properties
quarkus.http.auth.permission.file.enabled=false
#quarkus.http.auth.permission.jpa.enabled=false
#quarkus.http.auth.permission.ldap.enabled=false
```

### Access tokens
To obtain an access token, you need to first authenticate yourself via Basic Authentication:

> http -a [username]:[password] post :8080/apim/auth/token/bearer  
> { "access_token": "...." }

Your credentials are obviously stored either in the database, ldap or in the properties file, depending on which auth module is selected. 

After this, you can access the APIM with the access token:

> http -A bearer -a [access_token] :8080/gateway/... subscription-key:[your key]

### Configuration parameters

The configuration can either be put in `/apim-application/src/main/resources/application.properties` (recommended) or in `/apim-auth-jwt/src/main/resources/application.properties`.

Here are the parameters that must be overridden for production usage:

```properties
mp.jwt.verify.publickey.location=the keystore file which contains the certificate/public-key to verify the JWT tokens (must end with .p12)
smallrye.jwt.sign.key.location=the keystore file which contains the private-key to sign the JWT tokens (must end with .p12)
smallrye.jwt.keystore.password=password for the keystore file
smallrye.jwt.keystore.verify.key.alias=the alias used in the keystore file for the certificate
smallrye.jwt.keystore.sign.key.alias=the alias used in the keystore file for the private-key
rt.expiration.days=the expiration time in days for the refresh token, needed for apim-dashboard
```

### Dashboard configuration

The `isOidc` parameter needs to be set to `false` in: 

`/apim-dashboard-alpinejs/src/main/resources/META-INF/resources/js/apim.js`, on line 17.