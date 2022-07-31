package world.cepi.kstom.command.kommand

import net.kyori.adventure.text.Component
import net.minestom.server.command.CommandSender
import net.minestom.server.command.builder.arguments.Argument
import net.minestom.server.entity.Player
import world.cepi.kstom.util.hasDeepPermission

/**
 * @param permission A permission that will be checked before executing the command.
 * @param permissionMessage A supplier of a message that will be sent to the player if he doesn't have the permission
 * to use the command like they did. It takes a String as argument, it is the permission that is missing.
 */
@Suppress("MemberVisibilityCanBePrivate")
class KSyntax(
    vararg val arguments: Argument<*>,
    override val conditions: MutableList<Kommand.ConditionContext.() -> Boolean> = mutableListOf(),
    override val kommandReference: Kommand,
    val permission: String? = null,
    val permissionMessage: ((String) -> Component)? = null
) : Kondition<KSyntax>() {
    override val t: KSyntax
        get() = this

    operator fun invoke(executor: Kommand.SyntaxContext.() -> Unit) {
        if (arguments.isEmpty()) {
            kommandReference.command.setDefaultExecutor { sender, context ->

                if (!conditionPasses(Kommand.ConditionContext(sender, sender as? Player, context.input))) return@setDefaultExecutor

                if (!checkPermAndSendMessage(sender)) return@setDefaultExecutor

                executor(Kommand.SyntaxContext(sender, context))
            }
        } else {
            kommandReference.command.addConditionalSyntax(
                { sender, string -> conditionPasses(Kommand.ConditionContext(sender, sender as? Player, string ?: "")) },
                { sender, context ->
                    if (!checkPermAndSendMessage(sender)) return@addConditionalSyntax
                    executor(Kommand.SyntaxContext(sender, context))
                },
                *arguments
            )
        }
    }

    private fun checkPermAndSendMessage(sender: CommandSender): Boolean {
        if (permission != null) {
            if (!sender.hasDeepPermission(permission)) {
                sender.sendMessage((permissionMessage ?: kommandReference.defaultPermissionMessage)(permission))
                return false
            }
        } else if (kommandReference.defaultPermission != null) {
            if (!sender.hasDeepPermission(kommandReference.defaultPermission!!)) {
                sender.sendMessage((permissionMessage ?: kommandReference.defaultPermissionMessage)(kommandReference.defaultPermission!!))
                return false
            }
        }
        return true
    }
}