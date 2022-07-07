package art.arcane.edict;

import art.arcane.edict.context.SystemContext;
import art.arcane.edict.context.UserContext;
import art.arcane.edict.handlers.ContextHandler;
import art.arcane.edict.handlers.ContextHandlerRegistry;
import art.arcane.edict.handlers.ParameterHandlerRegistry;
import art.arcane.edict.handlers.ParameterHandler;
import art.arcane.edict.handlers.handlers.*;
import art.arcane.edict.message.Message;
import art.arcane.edict.message.StringMessage;
import art.arcane.edict.permission.Permission;
import art.arcane.edict.user.SystemUser;
import art.arcane.edict.user.User;
import art.arcane.edict.virtual.VCommandable;
import art.arcane.edict.virtual.VClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The main System.
 */
public class Edict {

    /**
     * Default parameter handlers.
     */
    private static final ParameterHandler<?>[] defaultHandlers = new ParameterHandler[]{
            new BooleanHandler(),
            new ByteHandler(),
            new DoubleHandler(),
            new FloatHandler(),
            new IntegerHandler(),
            new LongHandler(),
            new ShortHandler(),
            new StringHandler()
    };

    /**
     * Default permission factory.
     */
    private static final BiFunction<@Nullable Permission, @NotNull String, @NotNull Permission> defaultPermissionFactory = (parent, s) -> new Permission() {
        @Override
        public Permission getParent() {
            return parent;
        }

        @Override
        public String toString() {
            return s;
        }
    };

    /**
     * Default system user.
     */
    private static final SystemUser defaultSystemUser = new SystemUser();

    /**
     * Root commands
     */
    private final List<VCommandable> rootCommands = new ArrayList<>();

    /**
     * Permission factory
     */
    private final BiFunction<@Nullable Permission, @NotNull String, @NotNull Permission> permissionFactory;

    /**
     * Handler registry.
     */
    private final ParameterHandlerRegistry parameterHandlerRegistry;

    /**
     * Context handler registry.
     */
    private final ContextHandlerRegistry contextHandlerRegistry;

    /**
     * System user.
     */
    private final SystemUser systemUser;

    /**
     * Create a new command system.<br>
     * Uses default values:<br>
     *  - PermissionFactory: {@link #defaultPermissionFactory}<br>
     *  - EDictionary: settings, uses defaults of that class (by {@link EDictionary})<br>
     *  - ConfigFile: {@link EDictionary#defaultConfigFile}<br>
     *  - SystemUser: {@link SystemUser} (Using System.out.)<br>
     *  - ParameterHandlers: {@link #defaultHandlers}<br>
     *  - ContextHandlers: {@code None}
     */
    public Edict(@NotNull Object... commandRoots) {
        this(List.of(commandRoots), defaultPermissionFactory, new EDictionary(), EDictionary.defaultConfigFile, defaultSystemUser, defaultHandlers, new ContextHandler<?>[]{});
    }

    /**
     * Create a new command system.
     * @param commandRoots the roots of the commands.
     * @param permissionFactory factory to create permissions.
     * @param settings settings.
     * @param systemUser the user to output system messages to.
     * @param parameterHandlers the handlers you wish to register.
     * @param contextHandlers the context handlers you wish to register.
     */
    public Edict(@NotNull List<Object> commandRoots, @NotNull BiFunction<@Nullable Permission, @NotNull String, @NotNull Permission> permissionFactory, @NotNull EDictionary settings, @NotNull File configFile, @NotNull SystemUser systemUser, @NotNull ParameterHandler<?>[] parameterHandlers, @NotNull ContextHandler<?>[] contextHandlers) {

        // System
        this.systemUser = systemUser;

        // Permission factory
        this.permissionFactory = permissionFactory;

        // Settings
        try {
            EDictionary.setup(settings, configFile);
        } catch (IOException e) {
            w(new StringMessage("Could not setup settings due to IOException on custom config file location " + configFile + " because of " + e));
            try {
                EDictionary.setup(settings, EDictionary.defaultConfigFile);
            } catch (IOException ee) {
                w(new StringMessage("Could not setup settings due to IOException on default config file location " + EDictionary.defaultConfigFile + " because of " + e));
                w(new StringMessage("Will be using only in-system settings (these are reset when the program reboots)"));
            }
        }

        // System settings root
        if (EDictionary.get().settingsAsCommands) {
            VClass vRoot = VClass.fromInstance(EDictionary.get(), null, this);
            if (vRoot == null) {
                w(new StringMessage("Could not register settings commands!"));
            } else {
                rootCommands.add(vRoot);
            }
        }

        // Command Roots
        for (Object root : commandRoots) {
            VClass vRoot = VClass.fromInstance(root, null, this);
            if (vRoot == null) {
                w(new StringMessage("Could not register root " + root.getClass().getSimpleName() + "!"));
                continue;
            }
            rootCommands.add(vRoot);
        }

        // Handlers
        this.parameterHandlerRegistry = new ParameterHandlerRegistry(defaultHandlers);
        if (parameterHandlers != null) {
            for (ParameterHandler<?> handler : parameterHandlers) {
                parameterHandlerRegistry.register(handler);
                d(new StringMessage("Registered handler: " + handler.getClass().getSimpleName()));
            }
        }

        // Context handlers
        this.contextHandlerRegistry = new ContextHandlerRegistry();
        if (contextHandlers != null) {
            for (ContextHandler<?> contextHandler : contextHandlers) {
                contextHandlerRegistry.register(contextHandler);
                d(new StringMessage("Registered context handler: " + contextHandler.getClass().getSimpleName()));
            }
        }
    }

    /**
     * Run a command through the system.
     * @param command the command to run
     * @param user the user that ran the command
     */
    public void command(@NotNull String command, @NotNull User user) {

        // TODO: Threading

        command = cleanCommand(command);

        i(new StringMessage(user.name() + " sent command: " + command));

        List<String> input = List.of(command.split(" "));

        // Blank check
        if (input.isEmpty()) {
            // TODO: Send help
            user.send(new StringMessage("This is an empty command wtf do you want"));
            return;
        }

        d(new StringMessage("Running command: " + command));

        // Loop over roots
        // TODO: Threading here too?
        // TODO: Make sure the context is set right!
        new UserContext().post(user);
        new SystemContext().post(this);
        for (VCommandable root : VClass.sortAndFilterChildren(rootCommands, input.get(0), user, settings().matchThreshold)) {
            if (root.run(input.subList(1, input.size()), user)) {
                return;
            }
        }

        d(new StringMessage("Could not find suitable command for input: " + command));
        user.send(new StringMessage("Failed to run any commands for your input. Please try (one of): " + String.join(", ", rootCommands.stream().map(VCommandable::name).toList())));
    }

    /**
     * Clean the input command.
     * Performs the following actions:<br>
     *  - Remove all double spaces<br>
     *  - Remove spaces before equal signs
     * @param command the input command
     * @return the cleaned command
     */
    public @NotNull String cleanCommand(@NotNull String command) {
        command = command.strip();
        while (command.contains("  ")) {
            command = command.replace("  ", " ");
        }
        return command.replace(" =", "=");
    }

    /**
     * Make a {@link Permission} node.
     * @param input the input to make the node
     */
    public @NotNull Permission makePermission(@Nullable Permission parent, @NotNull String input) {
        return permissionFactory.apply(parent, input);
    }

    /**
     * Send an information message to the system.
     */
    public void i(Message message) {
        systemUser.i(message);
    }

    /**
     * Send a warning message to the system.
     */
    public void w(Message message) {
        systemUser.w(message);
    }

    /**
     * Send a debug message to the system.
     */
    public void d(Message message) {
        systemUser.d(message);
    }

    /**
     * Get system settings.
     * @return the system settings
     */
    public EDictionary settings() {
        return EDictionary.get();
    }
}
