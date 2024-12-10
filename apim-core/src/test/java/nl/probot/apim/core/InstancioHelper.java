package nl.probot.apim.core;

import nl.probot.apim.core.rest.dto.ApiPOST;
import org.instancio.Instancio;
import org.instancio.Model;
import org.instancio.settings.Settings;

import static org.instancio.Select.field;
import static org.instancio.settings.Keys.BEAN_VALIDATION_ENABLED;

public final class InstancioHelper {

    public static Settings settings = Settings.create().set(BEAN_VALIDATION_ENABLED, true);

    public static Model<ApiPOST> apiModel = Instancio.of(ApiPOST.class)
            .supply(field(ApiPOST::proxyPath), gen -> "/" + gen.alphanumeric(5))
            .withSettings(settings)
            .toModel();
}
