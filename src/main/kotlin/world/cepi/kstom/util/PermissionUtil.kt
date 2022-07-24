package world.cepi.kstom.util

import net.minestom.server.command.ConsoleSender
import net.minestom.server.entity.Player
import net.minestom.server.permission.Permission
import net.minestom.server.permission.PermissionHandler
import org.jglrxavpok.hephaistos.nbt.NBTCompound
import java.util.*

/**
 * Adds a string permission with NBT to the player without the object overhead.
 *
 * @param string The string to add to the player's permissions.
 * @param nbt The additional NBT data to add to the permission.
 */
fun Player.addPermission(string: String, nbt: NBTCompound? = null): Unit = this.addPermission(Permission(string, nbt))


private val permissionsCache = WeakHashMap<PermissionHandler, WeakHashMap<String, Boolean>>()

/**
 * Not only this checks for the permission, but this also checks for "sub-permissions".
 * For example, if you check for the permission "a.b.c", this will also check for "a.b.*" "a.*" and "*" (along with
 * "a.b" and "a"). If the [PermissionHandler] has one of these permissions, this will return true.
 *
 * Warning: do not forget to call [discardCache] when you change permissions because this uses caching.
 */
fun PermissionHandler.hasDeepPermission(permission: String): Boolean {
    // maybe add a toggleable logger to debug permissions issues
    return if (this is ConsoleSender) true
    else permissionsCache
        .computeIfAbsent(this) { WeakHashMap() }
        .computeIfAbsent(permission) { permission1: String ->
            this.hasDeepPermissionWithoutCache(
                permission1
            )
        }
}

private fun PermissionHandler.hasDeepPermissionWithoutCache(permission: String): Boolean {
    if (hasPermission(permission)) return true
    if (hasPermission("*")) return true
    val permPoints: MutableList<String> = permission.split(".").toMutableList()
    permPoints.removeLast() // we already checked the full permission
    while (permPoints.isNotEmpty()) { // we already checked for the case of 0 permPoints: *
        val newPerm = permPoints.joinToString(".")
        if (hasPermission(newPerm) || hasPermission("$newPerm.*"))
            return true
        permPoints.removeLast()
    }
    return false
}

fun discardCache() {
    permissionsCache.clear()
}