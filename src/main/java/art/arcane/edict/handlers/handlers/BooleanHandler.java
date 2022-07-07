package art.arcane.edict.handlers.handlers;


import art.arcane.edict.exception.ParsingException;
import art.arcane.edict.handlers.ParameterHandler;
import art.arcane.edict.util.Randoms;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BooleanHandler implements ParameterHandler<Boolean> {
    @Override
    public @NotNull List<Boolean> getPossibilities() {
        return List.of(true, false);
    }

    @Override
    public String toString(Boolean aByte) {
        return aByte.toString();
    }

    @Override
    public Boolean parse(String in, boolean force, String parameterName) throws ParsingException {
        if (in.equalsIgnoreCase("null") || in.equalsIgnoreCase("other") || in.equalsIgnoreCase("flip") || in.equalsIgnoreCase("toggle")) {
            return null;
        }
        try {
            return Boolean.parseBoolean(in);
        } catch (Throwable e) {
            throw new ParsingException(Boolean.class, parameterName, in, e);
        }
    }

    @Override
    public boolean supports(Class<?> type) {
        return type.equals(Boolean.class) || type.equals(boolean.class);
    }

    private static final List<String> defaults = List.of("true", "false", "other", "flip", "toggle");

    @Override
    public String getRandomDefault() {
        return defaults.get(Randoms.irand(0, defaults.size()));
    }
}