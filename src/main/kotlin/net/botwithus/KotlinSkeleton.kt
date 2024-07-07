package net.botwithus

import net.botwithus.api.game.hud.inventories.Backpack
import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.game.Client
import net.botwithus.rs3.game.Coordinate
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery
import net.botwithus.rs3.script.LoopingScript
import net.botwithus.rs3.script.config.ScriptConfig
import net.botwithus.rs3.events.impl.InteractionEvent
import net.botwithus.rs3.events.impl.ServerTickedEvent
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery
import net.botwithus.rs3.game.movement.Movement
import net.botwithus.rs3.game.movement.NavPath
import net.botwithus.rs3.game.movement.TraverseEvent
import net.botwithus.rs3.game.hud.interfaces.Interfaces
import net.botwithus.rs3.script.Execution
import net.botwithus.rs3.util.Regex
import java.util.*

class KotlinSkeleton(
    name: String,
    private val scriptConfig: ScriptConfig,
    scriptDefinition: ScriptDefinition
) : LoopingScript(name, scriptConfig, scriptDefinition) {

    lateinit var selectedNpc: NpcInfo
    lateinit var selectedNpc2: NpcInfo
    private val random = Random()
    var botState = BotState.IDLE
    private val walkQueue: Queue<Coordinate> = LinkedList()
    var searchNpc = ""
    var searchNpc2 = ""
    enum class BotState {
        IDLE,
    }

    data class NpcInfo(val id: Int, val name: String, val coordinate: Coordinate)

    val npcList = mutableListOf<NpcInfo>()
    val favourites = mutableListOf<NpcInfo>()

    init {
        loadNpcData()
    }

    private fun loadNpcData() {
        try {
            println("Loading NPC Data")
            scriptConfig.load()

            // Clear npcList first to prevent duplicates
            npcList.clear()

            // Load npcData
            scriptConfig.getProperty("npcData")?.split(";")?.forEach { line ->
                val parts = line.split(",")
                if (parts.size == 5) {
                    val id = parts[0].toInt()
                    val name = parts[1]
                    val x = parts[2].toInt()
                    val y = parts[3].toInt()
                    val z = parts[4].toInt()
                    val coordinate = Coordinate(x, y, z)
                    val npcInfo = NpcInfo(id, name, coordinate)
                    npcList.add(npcInfo)
                }
            }

            // Load favourites (if needed)
            scriptConfig.getProperty("favourites")?.split(",")?.mapNotNull { it.toIntOrNull() }?.forEach { id ->
                val npc = npcList.find { it.id == id }
                if (npc != null && !favourites.contains(npc)) {
                    favourites.add(npc)
                }
            }
        } catch (e: Exception) {
            println("Error loading NPC data: ${e.message}")
        }
    }

    private fun saveNpcData() {
        try {
            println("Saving NPC data")
            val npcData = npcList.joinToString(";") { "${it.id},${it.name},${it.coordinate.x},${it.coordinate.y},${it.coordinate.z}" }
            val favouriteData = favourites.joinToString(",") { "${it.id}" }
            scriptConfig.addProperty("npcData", npcData)
            scriptConfig.addProperty("favourites", favouriteData)
            scriptConfig.save()
            loadNpcData()
        } catch (e: Exception) {
            println("Error saving NPC data: ${e.message}")
        }
    }

    fun addToFavourites(npc: NpcInfo) {
        if (!favourites.contains(npc)) {
            favourites.add(npc)
            saveNpcData()
        }
    }

    fun removeFromFavourites(npc: NpcInfo) {
        if (favourites.contains(npc)) {
            favourites.remove(npc)
            saveNpcData()
        }
    }

    fun isNpcInFavourites(npc: NpcInfo): Boolean {
        return favourites.contains(npc)
    }

    private fun collectNpcData() {
        try {
            val npcPattern = Regex.getPatternForContainingOneOf("Talk to","Attack")
            val npcs = NpcQuery.newQuery().option(npcPattern).results()
            npcs.filterNot { isPlayerFamiliar(it) }.forEach { npc ->
                val coord = npc.coordinate
                if (coord != null) {
                    val npcInfo = NpcInfo(npc.configType?.id ?: 0, npc.configType?.name ?: "Unknown", coord)
                    if (npcList.none { it.id == npcInfo.id }) {
                        println("Adding new NPC: $npcInfo")
                        npcList.add(npcInfo)
                        saveNpcData()
                    }
                }
            }
        } catch (e: Exception) {
            println("Error collecting NPC data: ${e.message}")
        }
    }

    private fun isPlayerFamiliar(npc: net.botwithus.rs3.game.scene.entities.characters.npc.Npc): Boolean {
        val familiarIds = setOf(0,1)
        return familiarIds.contains(npc.configType?.id)
    }

    fun requestWalkTo(coordinate: Coordinate) {
        walkQueue.add(coordinate)
    }

    private fun processWalkQueue() {
        val coordinate = walkQueue.poll() ?: return
        try {
            println("Walking to coordinate: $coordinate")
            val path = NavPath.resolve(Area.Rectangular(Coordinate(coordinate.x+2,coordinate.y+2, coordinate.z), Coordinate(coordinate.x-2,coordinate.y-2, coordinate.z)).randomWalkableCoordinate)
            val results = Movement.traverse(path)
            if (results == TraverseEvent.State.NO_PATH) {
                println("Failed to find path to NPC")
                botState = BotState.IDLE
            } else {
                println("Reached NPC")
            }
        } catch (e: Exception) {
            println("Error walking to coordinate: ${e.message}")
            botState = BotState.IDLE
        }
    }

    override fun initialize(): Boolean {
        super.initialize()
        this.sgc = KotlinSkeletonGraphicsContext(this, console)

        try {
            println("Initializing Script")
            subscribe(ServerTickedEvent::class.java) {
                collectNpcData()
                processWalkQueue()
            }
            return true
        } catch (e: Exception) {
            println("Error initializing script: ${e.message}")
            return false
        }
    }

    override fun onLoop() {
        try {
            val player = Client.getLocalPlayer()
            if (Client.getGameState() != Client.GameState.LOGGED_IN || player == null || botState == BotState.IDLE) {
                Execution.delay(random.nextLong(2500, 5500))
                return
            }

            processWalkQueue()

            when (botState) {
                BotState.IDLE -> {
                    println("We're idle!")
                    Execution.delay(random.nextLong(1500, 5000))
                }
            }
        } catch (e: Exception) {
            println("Error in main loop: ${e.message}")
        }
    }

}
