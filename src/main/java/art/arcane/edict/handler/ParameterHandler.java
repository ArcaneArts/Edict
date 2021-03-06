package art.arcane.edict.handler;

import art.arcane.edict.exception.ParsingException;
import art.arcane.edict.exception.WhichException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Parameter handler.
 * @param <T> the type this handler can handle
 */
public interface ParameterHandler<T> {

    /**
     * Return a random value that may be entered
     * @return a random default value
     */
    String getRandomDefault();

    /**
     * Returns whether a certain type is supported by this handler<br>
     *
     * @param type the type to check
     * @return true if supported, false if not
     */
    boolean supports(Class<?> type);

    /**
     * Converting the type back to a string (inverse of the {@link #parse(String, String) parse} method)
     *
     * @param t the input of the designated type to convert to a String
     * @return the resulting string
     */
    String toString(T t);

    /**
     * Should parse a String into the designated type
     * @param in the string to parse
     * @param force force an option instead of throwing a {@link WhichException} if possible (can allow it throwing!)
     * @param parameterName the name of the parameter that is being parsed (only use this to create {@link ParsingException}s and {@link WhichException}s).
     * @return the value extracted from the string, of the designated type
     * @throws ParsingException thrown when the parsing fails (ex: "oop" translated to an integer throws this)
     * @throws WhichException thrown when multiple results are possible
     */
    @SuppressWarnings("RedundantThrows")
    T parse(String in, boolean force, String parameterName) throws ParsingException, WhichException;

    /**
     * Parse an input string to an output of the assigned type
     * @param in the input string
     * @param parameterName the name of the parameter that is being parsed (only use this to create {@link ParsingException}s and {@link WhichException}s).
     * @return the output type
     * @throws ParsingException when the input cannot be parsed into the output
     * @throws WhichException multiple outputs would be possible for the same input
     */
    default T parse(String in, String parameterName) throws ParsingException, WhichException {
        return parse(in, false, parameterName);
    }

    /**
     * Should return the possible values for this type
     *
     * @return possibilities for this type.
     */
    List<T> getPossibilities();

    /**
     * The possible entries for the inputted string (support for autocomplete on partial entries)
     *
     * @param input the inputted string to check against
     * @return a {@link List} of possibilities
     */
    default List<T> getPossibilities(@NotNull String input) {
        if (input.trim().isEmpty()) {
            return getPossibilities();
        }

        input = input.trim();
        List<T> possible = getPossibilities();
        List<T> matches = new ArrayList<>();

        if (possible == null) {
            return null;
        }

        if (possible.isEmpty()) {
            return matches;
        }

        if (input.isEmpty()) {
            return getPossibilities();
        }

        List<String> converted = possible.stream().map(v -> toString(v).trim()).toList();

        for (int i = 0; i < converted.size(); i++) {
            String g = converted.get(i);
            // if
            // G == I or
            // I in G or
            // G in I
            if (g.equalsIgnoreCase(input) || g.toLowerCase().contains(input.toLowerCase()) || input.toLowerCase().contains(g.toLowerCase())) {
                matches.add(possible.get(i));
            }
        }

        return matches;
    }

    /**
     * Forces conversion to the designated type before converting to a string using {@link #toString(T t)}
     *
     * @param t the object to convert to string (that should be of this type)
     * @return the resulting string.
     */
    @SuppressWarnings("unchecked")
    default String toStringForce(Object t) {
        return toString((T) t);
    }

    /**
     * Calculate integer multiplier value for an input<br>
     * Values used are<br>
     * - k > 1.000<br>
     * - m > 1.000.000<br>
     * - r > 512<br>
     * - h > 100<br>
     * - c > 16<br>
     * ! This does not return the actual value, just the multiplier!
     * @param value the input string
     * @return the multiplier
     */
    default int getMultiplier(@NotNull AtomicReference<String> value) {
        int multiplier = 1;
        String in = value.get();
        while (true) {
            if (in.toLowerCase().endsWith("k")) {
                multiplier *= 1000;
            } else if (in.toLowerCase().endsWith("m")) {
                multiplier *= 1000000;
            } else if (in.toLowerCase().endsWith("h")) {
                multiplier *= 100;
            } else if (in.toLowerCase().endsWith("c")) {
                multiplier *= 16;
            } else if (in.toLowerCase().endsWith("r")) {
                multiplier *= 512;
            } else {
                break;
            }
            in = in.substring(0, in.length() - 1);
        }

        value.set(in);
        return multiplier;
    }

    /**
     * Get a random int from to (inclusive)
     *
     * @param from the lower bound
     * @param to the upper bound (inclusive)
     * @return the value
     */
    default int randomInt(int from, int to) {
        return (int) randomFloat(from, to);
    }

    /**
     * Get a random float from to (inclusive)
     *
     * @param from the lower bound
     * @param to the upper bound
     * @return the value
     */
    default float randomFloat(float from, float to) {
        return from + (float) (Math.random() * ((to - from) + 1));
    }

    /**
     * Get a random double from to (inclusive)
     *
     * @param f the lower bound
     * @param t the upper bound
     * @return the value
     */
    default double randomDouble(double f, double t) {
        return f + (Math.random() * ((t - f) + 1));
    }
}
