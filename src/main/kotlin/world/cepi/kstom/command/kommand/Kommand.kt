package world.cepi.kstom.command.kommand

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.Command
import net.minestom.server.command.builder.CommandContext
import net.minestom.server.command.builder.CommandExecutor
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.command.builder.exception.ArgumentSyntaxException
import net.minestom.server.entity.Player
import org.jetbrains.annotations.Contract
import world.cepi.kstom.Manager
import kotlin.coroutines.CoroutineContext

@Suppress("unused", "MemberVisibilityCanBePrivate")
open class Kommand(k: Kommand.() -> Unit = {}, name: String, vararg aliases: String) : Kondition<Kommand>() {
    override val conditions: MutableList<ConditionContext.() -> Boolean> = mutableListOf()
    var playerCallbackFailMessage: (CommandSender) -> Unit = { }
    var consoleCallbackFailMessage: (CommandSender) -> Unit = { }

    override val t: Kommand
        get() = this
    override val kommandReference: Kommand by ::t

    val command = Command(name, *aliases)

    /**
     * A permission that will be checked before executing this command if no other permission is given in [syntax]
     */
    var defaultPermission: String? = null

    /**
     * A supplier of a message that will be sent to the player if he doesn't have the permission to use the command like
     * they did. It takes a String as argument, it is the permission that is missing.
     */
    lateinit var defaultPermissionMessage: (String) -> Component

    init {
        permissionMiniMessage(
            "<hover:show_text:'<grey><i>Lacking permission: </i><perm>'><red>Sorry! You don't have the permissions to do that!"
        )
        k()
    }

    data class SyntaxContext(val sender: CommandSender, val context: CommandContext) {

        val player by lazy { sender as Player }

        operator fun <T> get(argument: Argument<T>): T = context[argument]

        val commandName = context.commandName

        operator fun <T> Argument<T>.not(): T = context[this]
    }

    data class ConditionContext(val sender: CommandSender, val player: Player?, val input: String)
    data class ArgumentCallbackContext(val sender: CommandSender, val exception: ArgumentSyntaxException)

    @Contract(pure = true)
    fun syntax(
        vararg arguments: Argument<*> = arrayOf(),
    ) = KSyntax(*arguments, conditions = conditions.toMutableList(), kommandReference = this)

    @Contract(pure = true)
    fun syntax(
        vararg arguments: Argument<*> = arrayOf(),
        executor: SyntaxContext.() -> Unit
    ) = KSyntax(*arguments, conditions = conditions.toMutableList(), kommandReference = this).also { it.invoke(executor) }

    @Contract(pure = true)
    fun buildSyntax(vararg arguments: Argument<*>, block: KSyntaxBuilder.() -> Unit) = KSyntaxBuilder(*arguments).apply(block).execute()

    inner class KSyntaxBuilder(vararg val arguments: Argument<*>) {
        val conditions: MutableList<ConditionContext.() -> Boolean> = mutableListOf()
        var permission: String? = null
        var permissionMessage: ((String) -> Component)? = null
        var executor: SyntaxContext.() -> Unit = {}

        fun build() = KSyntax(arguments = arguments, conditions, this@Kommand, permission, permissionMessage)

        fun execute() = build().invoke(executor)
    }

    @Contract(pure = true)
    fun syntaxSuspending(
        context: CoroutineContext = Dispatchers.IO,
        vararg arguments: Argument<*> = arrayOf(),
        executor: suspend SyntaxContext.() -> Unit
    ) = KSyntax(*arguments, conditions = conditions.toMutableList(), kommandReference = this).invoke {
        CoroutineScope(context).launch { executor() }
    }

    inline fun argumentCallback(
        arg: Argument<*>,
        crossinline lambda: ArgumentCallbackContext.() -> Unit
    ) {
        command.setArgumentCallback({ source, value -> lambda(ArgumentCallbackContext(source, value)) }, arg)
    }

    inline fun <T> Argument<T>.failCallback(crossinline lambda: ArgumentCallbackContext.() -> Unit) {
        setCallback { sender, exception -> lambda(ArgumentCallbackContext(sender, exception)) }
    }

    inline fun default(crossinline block: SyntaxContext.() -> Unit) {
        command.defaultExecutor = CommandExecutor { sender, args -> block(SyntaxContext(sender, args)) }
    }

    inline fun defaultSuspending(context: CoroutineContext = Dispatchers.IO, crossinline block: suspend SyntaxContext.() -> Unit) {
        command.defaultExecutor = CommandExecutor { sender, args ->
            CoroutineScope(context).launch { block(SyntaxContext(sender, args)) }
        }
    }

    fun addSubcommands(vararg subcommands: Command) {
        subcommands.forEach { command.addSubcommand(it) }
    }

    fun addSubcommands(vararg subcommands: Kommand) {
        subcommands.forEach { command.addSubcommand(it.command) }
    }

    fun subcommand(name: String, vararg aliases: String, subK: Kommand.() -> Unit) {
        addSubcommands(Kommand(subK, name, *aliases))
    }

    fun register() {
        Manager.command.register(command)
    }

    fun unregister() {
        Manager.command.unregister(command)
    }

    /**
     * A shortcut for setting [defaultPermissionMessage] that automatically turns your message into a MiniMessage and replaces
     * <perm> with the permission that is missing.
     */
    fun permissionMiniMessage(message: String) {
        defaultPermissionMessage = { perm ->
            MiniMessage.miniMessage().deserialize(
                message,
                Placeholder.unparsed("perm", perm)
            )
        }
    }

}