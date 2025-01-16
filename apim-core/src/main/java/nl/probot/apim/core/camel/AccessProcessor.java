package nl.probot.apim.core.camel;

import io.quarkus.logging.Log;
import io.quarkus.security.UnauthorizedException;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Singleton;
import nl.probot.apim.core.entities.AccessListEntity;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpConstants.REMOTE_ADDRESS;

@Singleton
public class AccessProcessor implements Processor {

    @Override
    @ActivateRequestContext
    public void process(Exchange exchange) {
        var ip = exchange.getIn().getHeader(REMOTE_ADDRESS, String.class);
        var hasAccess = AccessListEntity.hasAccess(ip);
        Log.debugf("%s is accessing the apim", ip);

        if (!hasAccess) {
            throw new UnauthorizedException("%s is blocked or has no access".formatted(ip));
        }
    }
}
