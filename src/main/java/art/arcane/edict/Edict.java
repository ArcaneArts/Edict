package art.arcane.edict;

import art.arcane.edict.completables.CompletableCommandsRegistry;
import art.arcane.edict.context.SystemContext;
import art.arcane.edict.context.UserContext;
import art.arcane.edict.handler.ContextHandler;
import art.arcane.edict.handler.ContextHandlers;
import art.arcane.edict.handler.ParameterHandler;
import art.arcane.edict.handler.ParameterHandlers;
import art.arcane.edict.handler.handlers.*;
import art.arcane.edict.message.Message;
import art.arcane.edict.message.StringMessage;
import art.arcane.edict.permission.Permission;
import art.arcane.edict.user.SystemUser;
import art.arcane.edict.user.User;
import art.arcane.edict.util.BKTreeIndexer;
import art.arcane.edict.util.EDictionary;
import art.arcane.edict.util.ParameterParser;
import art.arcane.edict.virtual.VClass;
import art.arcane.edict.virtual.VCommandable;
import lombok.Builder;
import lombok.Singular;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * The main System.
 * TODO: Colored text
 */
@SuppressWarnings("unused")
@Builder
public class Edict {

    @Singular
    private List<Object> roots;

    public static class EdictBuilder {
        public EdictBuilder parameterHandler(ParameterHandler<?> handler) {
            parameterHandlers$value.add(handler);
            return this;
        }
        public EdictBuilder contextHandler(ContextHandler<?> handler) {
            contextHandlers$value.add(handler);
            return this;
        }
    }

    @Builder
    public Edict(
            @NotNull List<Object> roots,
            @NotNull SystemUser systemUser,
            @NotNull Consumer<Runnable> syncRunner,
            @NotNull EDictionary settings,
            @NotNull BiFunction<@Nullable Permission, @NotNull String, @NotNull Permission> permissionFactory,
            @NotNull ParameterHandlers parameterHandlers,
            @NotNull ContextHandlers contextHandlers
    ) {
        this.roots = roots;
        this.systemUser = systemUser;
        this.syncRunner = syncRunner;
        this.settings = settings;
        this.permissionFactory = permissionFactory;
        this.parameterHandlers = parameterHandlers;
        this.contextHandlers = contextHandlers;

        // System settings root
        if (settings.settingsAsCommands) {
            VClass vRoot = VClass.fromInstance(settings, null, this);
            if (vRoot == null) {
                w(new StringMessage("Could not register settings commands!"));
            } else {
                rootCommands.add(vRoot);
            }
        }

        // Command Roots
        for (Object root : roots) {
            VClass vRoot = VClass.fromInstance(root, null, this);
            if (vRoot == null) {
                w(new StringMessage("Could not register root " + root.getClass().getSimpleName() + "!"));
                continue;
            }
            rootCommands.add(vRoot);
        }

        // Indexer
        indexer.addAll(rootCommands);
    }

    /**
     * Root commands
     */
    private final List<VCommandable> rootCommands = new ArrayList<>();

    /**
     * System indexer.
     */
    private final BKTreeIndexer indexer = new BKTreeIndexer();

    /**
     * Completable commands' registry.
     */
    private final CompletableCommandsRegistry completableCommandsRegistry = new CompletableCommandsRegistry();

    /**
     * System user.
     */
    @Builder.Default
    private SystemUser systemUser = new SystemUser();

    /**
     * Sync runner.
     */
    @Builder.Default
    private Consumer<Runnable> syncRunner = Runnable::run;

    /**
     * Settings.
     */
    @Builder.Default
    private EDictionary settings = new EDictionary();

    /**
     * Permission factory
     */
    @Builder.Default
    private BiFunction<@Nullable Permission, @NotNull String, @NotNull Permission> permissionFactory = (parent, s) -> new Permission() {
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
     * Handler registry.
     */
    @Builder.Default
    private ParameterHandlers parameterHandlers = new ParameterHandlers(
            new BooleanHandler(),
            new ByteHandler(),
            new DoubleHandler(),
            new FloatHandler(),
            new IntegerHandler(),
            new LongHandler(),
            new ShortHandler(),
            new StringHandler()
    );

    /**
     * Context handler registry.
     */
    @Builder.Default
    private ContextHandlers contextHandlers = new ContextHandlers();


//    /**
//     * Create a new command system.<br>
//     * Use {@link #command(String, User)} to run commands with the system.
//     * @param commandRoots the roots of the commands
//     * @param permissionFactory factory to create permissions
//     * @param settings settings
//     * @param systemUser the user to output system messages to
//     * @param parameterHandlers the handlers you wish to register
//     * @param contextHandlers the context handlers you wish to register
//     * @param syncRunner consumer of runnable, called for {@link art.arcane.edict.api.Command}s that have {@link Command#sync()} true
//     * @throws NullPointerException if the {@link ParameterHandler} for any of the parameters of any methods of the {@code commandRoots} or any of its children is not registered
//     * or if the {@link ContextHandler} for any of the contextual parameter of any methods of the {@code commandRoots} or any of its children is not registered
//     */
//    public Edict(@NotNull List<Object> commandRoots, @NotNull BiFunction<@Nullable Permission, @NotNull String, @NotNull Permission> permissionFactory, @NotNull EDictionary settings, @NotNull SystemUser systemUser, @NotNull ParameterHandler<?>[] parameterHandlers, @NotNull ContextHandler<?>[] contextHandlers, @NotNull Consumer<Runnable> syncRunner) throws NullPointerException {
//
//
//        // Settings
//        EDictionary.set(settings);
//
//        // System settings root
//        if (EDictionary.get().settingsAsCommands) {
//            VClass vRoot = VClass.fromInstance(EDictionary.get(), null, this);
//            if (vRoot == null) {
//                w(new StringMessage("Could not register settings commands!"));
//            } else {
//                rootCommands.add(vRoot);
//            }
//        }
//
//        // Command Roots
//        for (Object root : commandRoots) {
//            VClass vRoot = VClass.fromInstance(root, null, this);
//            if (vRoot == null) {
//                w(new StringMessage("Could not register root " + root.getClass().getSimpleName() + "!"));
//                continue;
//            }
//            rootCommands.add(vRoot);
//        }
//
//        // Indexer
//        indexer.addAll(rootCommands);
//
//        // Handlers
//        this.parameterHandlerRegistry = new ParameterHandlerRegistry(defaultHandlers);
//        if (parameterHandlers != null) {
//            for (ParameterHandler<?> handler : parameterHandlers) {
//                parameterHandlerRegistry.register(handler);
//                d(new StringMessage("Registered handler: " + handler.getClass().getSimpleName()));
//            }
//        }
//
//        // Context handlers
//        this.contextHandlerRegistry = new ContextHandlerRegistry();
//        if (contextHandlers != null) {
//            for (ContextHandler<?> contextHandler : contextHandlers) {
//                contextHandlerRegistry.add(contextHandler);
//                d(new StringMessage("Registered context handler: " + contextHandler.getClass().getSimpleName()));
//            }
//        }
//    }

    /**
     * Run a command through the system.
     * @param command the command to run
     * @param user the user that ran the command
     */
    public void command(@NotNull String command, @NotNull User user) {
        command(command, user, false);
    }

    /**
     * Run a command through the system.
     * @param command the command to run
     * @param user the user that ran the command
     * @param forceSync force the execution of this command in sync
     */
    public void command(@NotNull String command, @NotNull User user, boolean forceSync) {
        Runnable r = () -> {
            final String fCommand = ParameterParser.cleanCommand(command);

            i(new StringMessage(user.name() + " sent command: " + fCommand));

            List<String> input = List.of(fCommand.split(" "));

            // Blank check
            if (input.isEmpty()) {
                // TODO: Send root command help
                user.send(new StringMessage("This is an empty command wtf do you want"));
                return;
            }

            d(new StringMessage("Running command: " + fCommand));

            // Loop over roots
            new UserContext().post(user);
            new SystemContext().post(this);

            for (VCommandable root : indexer.search(input.get(0), getSettings().matchThreshold, (vCommandable -> user.hasPermission(vCommandable.permission())))) {
                d(new StringMessage("Running root: " + root.getClass().getSimpleName()));
                if (root.run(input.subList(1, input.size()), user)) {
                    return;
                }
            }

            d(new StringMessage("Could not find suitable command for input: " + fCommand));
            user.send(new StringMessage("Failed to run any commands for your input. Please try (one of): " + String.join(", ", rootCommands.stream().map(VCommandable::name).toList())));

        };

        if (forceSync) {
            d(new StringMessage("Running command in forced sync. Likely for testing purposes."));
            r.run();
        } else {
            new Thread(r).start();
        }
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
    public EDictionary getSettings() {
        return settings;
    }

    /**
     * Get the {@link ParameterHandlers}.
     * @return the {@link ParameterHandlers}
     */
    public ParameterHandlers getParameterHandlers() {
        return parameterHandlers;
    }

    /**
     * Get the {@link ContextHandlers}.
     * @return the {@link ContextHandlers}
     */
    public ContextHandlers getContextHandlers() {
        return contextHandlers;
    }

    /**
     * Get the {@link CompletableCommandsRegistry}.
     * @return the {@link CompletableCommandsRegistry}
     */
    public CompletableCommandsRegistry getCompletableCommandsRegistry() {
        return completableCommandsRegistry;
    }

    /**
     * Run a runnable in sync, using the {@link #syncRunner}.
     * @param runnable the runnable to run
     */
    public void runSync(Runnable runnable) {
        syncRunner.accept(runnable);
    }
}
