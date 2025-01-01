package nl.probot.apim.core.entities.functions;

import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;

import static org.hibernate.type.StandardBasicTypes.BOOLEAN;

public class ArrayContainsFunction implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions fc) {
        var resultType = fc.getTypeConfiguration().getBasicTypeRegistry().resolve(BOOLEAN);
        fc.getFunctionRegistry().registerPattern("array_any", "?2 = ANY(?1)", resultType);
    }
}
