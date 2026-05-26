package dev.reigaserver.matching

import com.mojang.brigadier.Command
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import net.kyori.adventure.text.Component
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class Matching : JavaPlugin() {
    lateinit var settings: MatchingSettings
    val matchingGroups = ConcurrentHashMap<UUID, MatchingGroup>()
    val minQueueGroupNum = 3


    override fun onEnable() {
        saveDefaultConfig()
        settings = MatchingSettings.fromConfig(config)

        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(buildCommandNode())
        }

        repeat(minQueueGroupNum) {
            matchingGroups[UUID.randomUUID()] = MatchingGroup(mutableListOf(), settings.minPlayers)
        }
    }

    override fun onDisable() {

    }

    fun buildCommandNode(): LiteralCommandNode<CommandSourceStack> {
        val enqueueSubCommand = Commands.literal("enqueue")
            .requires { it.sender is Player }
            .executes {
                val p = it.source.sender as Player
                val resultMsg: String = synchronized(matchingGroups) {
                    matchingGroups.values
                        .filter { m -> !m.manuallyCreated && !m.isFull() }
                        .run {
                            getOrNull(Random.nextInt(0, size.coerceAtLeast(1)))
                                ?.run {
                                    this.players.add(p)
                                    "キューに登録しました。マッチングするまでお待ちください"
                                } ?: "マッチンググループが見つかりませんでした。しばらくしてから再度お試しください"
                        }
                }
                it.source.sender.sendMessage(Component.text(resultMsg))

                Command.SINGLE_SUCCESS
            }
        val dequeueSubCommand = Commands.literal("dequeue")
            .requires { it.sender is Player }
            .executes {
                val p = it.source.sender as Player
                val resultMsg = synchronized(matchingGroups) {
                    matchingGroups.values
                        .find { m -> m.players.contains(p) }
                        ?.run {
                            this.players.remove(p)
                            "キューから離脱しました"
                        } ?: "現在キューに登録されていません"
                }

                it.source.sender.sendMessage(Component.text(resultMsg))
                Command.SINGLE_SUCCESS
            }
        val helpSubCommand = Commands.literal("help")
            .executes {
                it.source.sender.sendMessage(Component.text("matching command manual"))
                Command.SINGLE_SUCCESS
            }
        val reloadSubCommand = Commands.literal("reload")
            .executes {
                reloadConfig()
                this.settings = MatchingSettings.fromConfig(this.config)
                Command.SINGLE_SUCCESS
            }
        return Commands.literal("matching")
            .then(enqueueSubCommand)
            .then(dequeueSubCommand)
            .then(reloadSubCommand)
            .then(helpSubCommand)
            .executes {
                it.source.sender.sendMessage(
                    Component.text("usage: /matching <enqueue|dequeue|help>")
                        .appendNewline()
                        .append(Component.text("Run '/matching help' to show more detailed manuals."))
                )
                Command.SINGLE_SUCCESS
            }
            .build()
    }
}

data class MatchingSettings(
    var minPlayers: Int,
    var allowForceStart: Boolean,
    var disbandAfterMinutes: Int,
    var onMatch: String,
) {
    companion object {
        fun fromConfig(config: FileConfiguration): MatchingSettings {
            return MatchingSettings(
                minPlayers = config.getInt("minPlayers", 5),
                allowForceStart = config.getBoolean("allowForceStart", false),
                disbandAfterMinutes = config.getInt("disbandAfterMinutes", 5),
                onMatch = config.getString("onMatch") ?: ""
            )
        }
    }
}

data class MatchingGroup(
    var players: MutableList<Player>,
    var maxPlayer: Int,
    val manuallyCreated: Boolean = false
) {
    fun isFull(): Boolean = players.size == maxPlayer
}
