package art.arcane.edict.handler.handlers;


import art.arcane.edict.exception.ParsingException;
import art.arcane.edict.handler.ParameterHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class FloatHandler implements ParameterHandler<Float> {
    @Override
    public List<Float> getPossibilities() {
        return null;
    }

    @Override
    public @NotNull Float parse(String in, boolean force, String parameterName) throws ParsingException {
        try {
            AtomicReference<String> r = new AtomicReference<>(in);
            double m = getMultiplier(r);
            return (float) (Float.parseFloat(r.get()) * m);
        } catch (Throwable e) {
            throw new ParsingException(Float.class, parameterName, in, e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Float.class) || type.equals(float.class);
    }

    @Override
    public String toString(Float f) {
        return f.toString();
    }

    @Override
    public String getRandomDefault() {
        return String.valueOf(randomFloat(0, 99.99f));
    }
}
