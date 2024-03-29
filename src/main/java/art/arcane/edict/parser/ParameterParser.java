package art.arcane.edict.parser;

import art.arcane.edict.Edict;
import art.arcane.edict.exception.ContextMissingException;
import art.arcane.edict.exception.ParsingException;
import art.arcane.edict.exception.WhichException;
import art.arcane.edict.message.ClickableMessage;
import art.arcane.edict.message.StringMessage;
import art.arcane.edict.user.User;
import art.arcane.edict.virtual.VParam;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.*;

/**
 * Parameter parser.
 */
public class ParameterParser {

    /**
     * Whether to dump or not.
     */
    private static final boolean ENABLE_DEBUG = false;

    /**
     * Input strings.
     */
    private final @NotNull List<String> input;

    /**
     * Parameters that need values.
     */
    private final @NotNull List<VParam> params;

    /**
     * Remaining parameters.
     */
    private final @NotNull List<VParam> remainingParams;

    /**
     * The user running the command.
     */
    private final @NotNull User user;

    /**
     * The system in which the command is being run.
     */
    private final @NotNull Edict system;

    /**
     * Mapping from parameters to objects.
     */
    private final ConcurrentHashMap<VParam, Object> values = new ConcurrentHashMap<>();

    /**
     * Mapping from parameters to (one of) the argument(s).
     */
    private final ConcurrentHashMap<VParam, String> inputs = new ConcurrentHashMap<>();

    /**
     * Mapping from (one of) the arguments(s) to the reason why it is bad.
     */
    private final ConcurrentHashMap<String, String> badArgs = new ConcurrentHashMap<>();

    /**
     * Arguments that started with '-' indicating a true boolean (flag).
     */
    private final List<String> dashBooleanArgs = new ArrayList<>();

    /**
     * Arguments without a key (just a string).
     */
    private final List<String> keylessArgs = new ArrayList<>();

    /**
     * Arguments with a key (key=value).
     */
    private final List<String> keyedArgs = new ArrayList<>();
    /**
     * Parameters not fulfilled.
     */
    private final List<VParam> missingInputs = new ArrayList<>();

    /**
     * Create a new parser
     * @param input the input strings
     * @param params the parameters that need values
     * @param user the user running the command
     * @param system the system in which the command is being run
     */
    public ParameterParser(@NotNull List<String> input, @NotNull List<VParam> params, @NotNull User user, @NotNull Edict system) {
        this.input = input;
        this.params = params;
        this.remainingParams = new ArrayList<>(params);
        this.user = user;
        this.system = system;
    }

    /**
     * Clean the input command.
     * Performs the following actions:<br>
     *  - Remove all double spaces<br>
     *  - Remove spaces before equal signs<br>
     *  - Remove all double ='s and -'s
     * @param command the input command
     * @return the cleaned command
     */
    public static @NotNull String cleanCommand(@NotNull String command) {
        // TODO: Allow some of this stuff in parameters.
        while (command.contains("  ")) {
            command = command.replace("  ", " ");
        }
        while (command.contains("==")) {
            command = command.replace("==", "=");
        }
        while (command.contains("--")) {
            command = command.replace("--", "-");
        }
        return command.replace(" =", "=").replace("= ", "=");
    }

    /**
     * Parse inputs.
     * @return an array of parameter values, or {@code null} if insufficient input was provided.
     * In the case it is {@code null}, {@link #missingInputs} is non-empty and shows which parameters are missing.
     * @throws RuntimeException in case a bug in the system causes invalid states. This would be a problem with Edict.
     */
    public @Nullable Object[] parse() throws RuntimeException {
        dump("Initial");
        divideInput();
        dump("Divide Input");
        assignKeyed();
        dump("Assign Keyed");
        assignDashBoolean();
        dump("Dash Boolean");
        assignKeyless();
        dump("Assign Keyless");
        assignDefaults();
        dump("Assign Defaults");
        if (!checkSufficientInput()) {
            dump("Check Sufficient Input Failed");
            return null;
        }
        parseContextual();
        dump("Parse Contextual");
        parseInputs();
        dump("Parse Inputs");
        checkAllValues();
        dump("Check Values");
        return getResult();
    }

    /**
     * Divide the input nodes into separate groups based on certain properties.
     */
    private void divideInput() {
        while (!input.isEmpty()) {
            String argument = input.remove(0);
            List<String> splitByEquals = List.of(argument.split("="));

            switch (splitByEquals.size()) {
                case 1 -> {
                    if (argument.startsWith("-")) {
                        dashBooleanArgs.add(argument.substring(1));
                    } else {
                        keylessArgs.add(argument);
                    }
                }
                case 2 -> {
                    if (splitByEquals.get(1).isEmpty()) {
                        badArgs.put(argument, "Empty value after splitting on '='");
                    } else {
                        keyedArgs.add(argument);
                    }
                }
                default -> badArgs.put(argument, "Too many '=' signs in input");
            }
        }
    }

    /**
     * Assign keyed parameters to their {@link VParam}.
     */
    private void assignKeyed() {
        loop: while (!keyedArgs.isEmpty()) {
            String arg = keyedArgs.remove(0);
            String key = arg.split("\\Q=\\E")[0];
            String value = arg.split("\\Q=\\E")[1];

            for (VParam param : new ArrayList<>(remainingParams)) {
                if (param.allNames().contains(key)) {
                    remainingParams.remove(param);
                    inputs.put(param, value);
                    continue loop;
                }
            }

            for (VParam param : new ArrayList<>(remainingParams)) {
                for (String name : param.allNames()) {
                    if (name.contains(key)) {
                        remainingParams.remove(param);
                        inputs.put(param, value);
                        continue loop;
                    }
                }
            }

            for (VParam param : new ArrayList<>(remainingParams)) {
                for (String name : param.allNames()) {
                    if (key.contains(name)) {
                        remainingParams.remove(param);
                        inputs.put(param, value);
                        continue loop;
                    }
                }
            }

            badArgs.put(arg, "Could not match any parameter's name");
        }
    }

    /**
     * Assign dashed boolean arguments to their {@link VParam}.
     */
    private void assignDashBoolean() {
        loop: while (!dashBooleanArgs.isEmpty()) {
            String key = dashBooleanArgs.remove(0).substring(1);

            for (VParam param : new ArrayList<>(remainingParams)) {
                if (param.allNames().contains(key)
                        && param.parameter().getType().equals(Boolean.class)
                        || param.parameter().getType().equals(boolean.class)) {
                    remainingParams.remove(param);
                    inputs.put(param, "true");
                    continue loop;
                }
            }

            for (VParam param : new ArrayList<>(remainingParams)) {
                for (String name : param.allNames()) {
                    if (name.contains(key)
                            && param.parameter().getType().equals(Boolean.class)
                            || param.parameter().getType().equals(boolean.class)) {
                        remainingParams.remove(param);
                        inputs.put(param, "true");
                        continue loop;
                    }
                }
            }

            for (VParam param : new ArrayList<>(remainingParams)) {
                for (String name : param.allNames()) {
                    if (key.contains(name)
                            && param.parameter().getType().equals(Boolean.class)
                            || param.parameter().getType().equals(boolean.class)) {
                        remainingParams.remove(param);
                        inputs.put(param, "true");
                        continue loop;
                    }
                }
            }

            badArgs.put("-" + key, "Could not match any parameter's name");
        }
    }

    /**
     * Assign null arguments to their {@link VParam}.
     */
    private void assignKeyless() {
        while (!keylessArgs.isEmpty()) {
            inputs.put(remainingParams.remove(0), keylessArgs.remove(0));
        }
    }

    /**
     * Assign default values (if available) to their {@link VParam}.
     */
    private void assignDefaults() {
        for (VParam param : new ArrayList<>(remainingParams)) {
            if (!param.param().defaultValue().isBlank()) {
                // Has a default
                remainingParams.remove(param);
                inputs.put(param, param.param().defaultValue());
            }
        }
    }

    /**
     * Check inputs map to see if sufficient input was given by the user.
     * @return true if sufficient input was provided
     */
    private boolean checkSufficientInput() {
        for (VParam param : new ArrayList<>(remainingParams)) {
            if (!inputs.containsKey(param) && !(param.param().contextual() && user.canUseContext())) {
                missingInputs.add(param);
            }
        }
        return missingInputs.isEmpty();
    }

    /**
     * Parse contextual values.
     */
    private void parseContextual() {
        for (VParam param : new ArrayList<>(remainingParams)) {
            try {
                assert param.contextHandler() != null;
                Object value = param.contextHandler().handle(user);
                remainingParams.remove(param);
                values.put(
                        param,
                        value
                );
            } catch (ContextMissingException ignored) {

            }
        }
    }

    /**
     * Parse input values from {@link #inputs}.
     */
    private void parseInputs() {
        new HashMap<>(inputs).forEach((param, input) -> {
            inputs.remove(param);
            try {
                values.put(param, system.getParameterHandlers().getHandlerFor(param.parameter().getType()).parse(input, param.name()));
            } catch (ParsingException e) {
                badArgs.put(input, "Cannot parse this input to parameter " + param.name() + " of type " + param.parameter().getType().getSimpleName());
            } catch (WhichException e) {
                Object option = pickValidOption(user, e.getOptions(), param);
                if (option == null) {
                    missingInputs.add(param);
                    badArgs.put(e.getInput(), "Lead to multiple options, but none were picked.");
                    return;
                }
                values.put(param, option);
            }
        });
    }

    /**
     * Check all values in {@link #values}.
     * @throws RuntimeException if a value is null, missing or if the value is of an incorrect type
     */
    private void checkAllValues() throws RuntimeException {
        for (VParam param : remainingParams) {
            Object value = values.get(param);
            if (value == null) {
                throw new RuntimeException("A parameter is missing from the mapping, which should never happen!");
            }
            if (!(param.parameter().getType().isAssignableFrom(value.getClass()) && value.getClass().isAssignableFrom(param.parameter().getType()))) {
                throw new RuntimeException("A parameter has gotten a value assigned that is not of a valid type somehow!");
            }
        }
    }

    /**
     * Get resulting object array from {@link #values}.
     * @return the object array
     */
    private Object @NotNull [] getResult() {
        Object[] result = new Object[params.size()];
        for (int i = 0; i < params.size(); i++) {
            result[i] = values.get(params.get(i));
        }
        return result;
    }

    /**
     * Dump all data of this parser at any given time.
     */
    private void dump(String stage) {
        if (!ENABLE_DEBUG) { return; }
        system.d(new StringMessage(stage + " Dump for user: " + user.name()));
        if (!input.isEmpty()) { system.d(new StringMessage("Input: " + String.join(" / ", input))); }
        if (!remainingParams.isEmpty()) { system.d(new StringMessage("Params: " + String.join(" / ", remainingParams.stream().map(VParam::name).toList()))); }
        if (!values.isEmpty()) { system.d(new StringMessage("Values: " + String.join(" / ", values.keySet().stream().map(k -> k.name() + " > " + k.parameterHandler().toStringForce(values.get(k))).toList()))); }
        if (!inputs.isEmpty()) { system.d(new StringMessage("Inputs: " + String.join(" / " + inputs.keySet().stream().map(k -> k.name() + " = " + values.get(k))))); }
        if (!badArgs.isEmpty()) { system.d(new StringMessage("BadArgs: " + String.join(" / ", badArgs.keySet().stream().map(k -> k + " > " + badArgs.get(k)).toList()))); }
        if (!dashBooleanArgs.isEmpty()) { system.d(new StringMessage("DashArgs: " + String.join(" / " + dashBooleanArgs))); }
        if (!keylessArgs.isEmpty()) { system.d(new StringMessage("KeylessArgs: " + String.join(" / " + keylessArgs))); }
        if (!keyedArgs.isEmpty()) { system.d(new StringMessage("KeyedArgs: " + String.join(" / " + keyedArgs))); }
        if (!missingInputs.isEmpty()) { system.d(new StringMessage("MissingInputs: " + String.join(" / " + missingInputs.stream().map(VParam::name).toList()) + "\n")); }
        system.d(new StringMessage("End " + stage + " Dump"));
    }

    /**
     * Instruct the user to pick a valid option.
     * @param user The user that must pick an option
     * @param options The valid options that can be picked (as objects)
     * @return The string value for the selected option
     */
    private @Nullable Object pickValidOption(User user, List<?> options, VParam param) {

        if (system.getSettings().alwaysPickFirstOption) {
            return options.get(0);
        }

        List<String> values = new ArrayList<>();
        for (Object option : options) {
            values.add(param.parameterHandler().toStringForce(option));
        }

        user.send(new StringMessage("Pick a " + param.name() + " (" + param.parameter().getType().getSimpleName() + ")"));
        user.send(new StringMessage("This query will expire in 15 seconds."));

        int tries = 0;
        Integer result = null;

        while (tries++ < system.getSettings().optionPickAttempts) {
            if (user.canUseClickable()) {
                user.send(new StringMessage("Please pick a valid option by clicking the option."));
                for (int i = 0; i < values.size(); i++) {
                    int finalI = i;
                    user.send(new ClickableMessage(values.get(i), () -> system.command(String.valueOf(finalI), user)));
                }
            } else {
                user.send(new StringMessage("Please pick a valid option by inputting the number before the option."));
                for (int i = 0; i < values.size(); i++) {
                    user.send(new StringMessage(i + ") " + values.get(i)));
                }
            }

            CompletableFuture<String> future = new CompletableFuture<>();
            system.getCompletableCommandsRegistry().register(user, future);
            user.playPickNotification();

            try {
                result = Integer.parseInt(future.get(system.getSettings().optionPickTimeout, TimeUnit.SECONDS));
            } catch (InterruptedException | ExecutionException ignored) {
                user.send(new StringMessage("Your input was interrupted, please try again"));
            } catch (TimeoutException ignored) {
                user.send(new StringMessage("Your input query timed out. Please enter your option within " + system.getSettings().optionPickTimeout + " seconds"));
            } catch (NumberFormatException ignored) {
                user.send(new StringMessage("Your input was not a number, and picking the option failed. Please enter a number."));
            }

            if (result == null) {
                continue;
            }

            return options.get(result);
        }

        user.send(new StringMessage("You did not enter a correct option within " + tries + " tries."));
        user.send(new StringMessage("Please re-run the command."));

        return null;
    }

    /**
     * Get the missing inputs for this parser.
     * @return the missing inputs
     */
    public List<VParam> getMissingInputs() {
        return missingInputs;
    }

    /**
     * Get bad arguments and the reasons for them.
     * @return the bad arguments and reasons
     */
    public List<String> getBadArgsAndReasons() {
        List<String> argsAndReasons = new ArrayList<>();
        badArgs.keys().asIterator().forEachRemaining(arg -> argsAndReasons.add("'" + arg + "' failed because of: " + badArgs.get(arg)));
        return argsAndReasons;
    }
}
