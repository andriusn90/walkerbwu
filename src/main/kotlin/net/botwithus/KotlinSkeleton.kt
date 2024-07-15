package net.botwithus

import net.botwithus.KotlinSkeleton.QuestObjects.questAnneDimitri
import net.botwithus.KotlinSkeleton.QuestObjects.questAnneDimitriLocation
import net.botwithus.KotlinSkeleton.QuestObjects.questAnneDimitriLocation2
import net.botwithus.KotlinSkeleton.QuestObjects.questAnneDimitriLocation3
import net.botwithus.KotlinSkeleton.QuestObjects.questAvaryss
import net.botwithus.KotlinSkeleton.QuestObjects.questBilrach
import net.botwithus.KotlinSkeleton.QuestObjects.questChaosDemon
import net.botwithus.KotlinSkeleton.QuestObjects.questEnakhra
import net.botwithus.KotlinSkeleton.QuestObjects.questGeneralKhazard
import net.botwithus.KotlinSkeleton.QuestObjects.questMemoryFragment
import net.botwithus.KotlinSkeleton.QuestObjects.questMoia
import net.botwithus.KotlinSkeleton.QuestObjects.questPointOfInterest
import net.botwithus.KotlinSkeleton.QuestObjects.questRestoredMemory
import net.botwithus.KotlinSkeleton.QuestObjects.questStartLocation
import net.botwithus.KotlinSkeleton.QuestObjects.questStartNPC
import net.botwithus.KotlinSkeleton.QuestObjects.questTrindine
import net.botwithus.KotlinSkeleton.QuestObjects.questWoundedCultist
import net.botwithus.KotlinSkeleton.QuestObjects.questZemouregal
import net.botwithus.api.game.hud.Dialog
import net.botwithus.internal.scripts.ScriptDefinition
import net.botwithus.rs3.events.impl.ServerTickedEvent
import net.botwithus.rs3.game.Area
import net.botwithus.rs3.game.Client
import net.botwithus.rs3.game.Coordinate
import net.botwithus.rs3.game.hud.interfaces.Interfaces
import net.botwithus.rs3.game.minimenu.MiniMenu
import net.botwithus.rs3.game.minimenu.actions.ComponentAction
import net.botwithus.rs3.game.minimenu.actions.SelectableAction
import net.botwithus.rs3.game.movement.Movement
import net.botwithus.rs3.game.movement.NavPath
import net.botwithus.rs3.game.movement.TraverseEvent
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery
import net.botwithus.rs3.game.scene.entities.characters.player.LocalPlayer
import net.botwithus.rs3.game.vars.VarManager
import net.botwithus.rs3.input.KeyboardInput
import net.botwithus.rs3.script.Execution
import net.botwithus.rs3.script.LoopingScript
import net.botwithus.rs3.script.ScriptConsole
import net.botwithus.rs3.script.config.ScriptConfig
import net.botwithus.rs3.util.Regex
import java.util.*
import java.util.regex.Pattern


class KotlinSkeleton(
    name: String,
    private val scriptConfig: ScriptConfig,
    scriptDefinition: ScriptDefinition
) : LoopingScript(name, scriptConfig, scriptDefinition) {

    lateinit var selectedNpc: NpcInfo
    lateinit var selectedNpc2: NpcInfo
    val random = Random()
    var botState = BotState.IDLE
    private val walkQueue: Queue<Coordinate> = LinkedList()
    var searchNpc = ""
    var searchNpc2 = ""
    var customLocation = ""
    var captureTimer = System.currentTimeMillis().toInt()
    var counterTalkingPeople = 0
    var talkedToTrindine: Boolean = false

    override fun delay(time: Long) {
        try {
            Execution.delay(random.nextLong(time, time + 200))
        } catch (e: InterruptedException) {
            // Handle the interruption gracefully
            ScriptConsole.println("Thread was interrupted during delay: ${e.message}")
            Thread.currentThread().interrupt() // Restore the interrupt status
        } catch (e: Exception) {
            ScriptConsole.println("Exception in delay: ${e.message}")
            throw e // Re-throw to propagate the exception to the caller
        }
    }

    enum class BotState {
        IDLE,
        DAUGHTER_OF_CHAOS,
    }

    data class NpcInfo(val id: Int, val name: String, val coordinate: Coordinate)

    val npcList = mutableListOf<NpcInfo>()
    val favourites = mutableListOf<NpcInfo>()

    init {
        loadNpcData()
    }

    override fun initialize(): Boolean {
        super.initialize()
        this.sgc = KotlinSkeletonGraphicsContext(this, console)

            println("Initializing Script")
            subscribe(ServerTickedEvent::class.java) {
                collectNpcData()
                processWalkQueue()
            }
        return true
    }

    override fun onLoop() {

        val player = Client.getLocalPlayer()
        if (Client.getGameState() != Client.GameState.LOGGED_IN || player == null || botState == BotState.IDLE) {
            Execution.delay(random.nextLong(2500, 5500))
            return
        }

        when (botState) {
            BotState.IDLE -> {
                ScriptConsole.println("We're idle!")
                Execution.delay(random.nextLong(1500, 5000))
            }
            BotState.DAUGHTER_OF_CHAOS -> {
                ScriptConsole.println("Running this shit.")
                delay(600)
                daughtersOfChaosQuest()
            }
        }
        return
    }


    private fun loadNpcData() {
        try {
            ///println("Loading NPC Data")
            scriptConfig.load()

            // Check if npcData is present in scriptConfig
            val npcDataProperty = scriptConfig.getProperty("npcData")
            if (npcDataProperty.isNullOrEmpty()) {
                val defaultNpcs = "24854,Grand Exchange (Varrock),3166,3486,0;30308,Kili (City of Um),1148,1807,1;30289,Malignius Mortifier (Necro Ritual Site),1035,1763,1"
                scriptConfig.addProperty("favourites", "24854,")
                scriptConfig.addProperty("npcData", defaultNpcs)
                println("No NPC data found in config.")
                loadNpcData()
                return
            }

            // Clear npcList first to prevent duplicates
            npcList.clear()

            // Load npcData
            npcDataProperty.split(";").forEach { line ->
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
            val uniqueNpcs = LinkedHashSet(npcList)

            val npcData = uniqueNpcs.joinToString(";") { "${it.id},${it.name},${it.coordinate.x},${it.coordinate.y},${it.coordinate.z}" }
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

    fun createCustomLocation(coord: Coordinate, customName: String) {
        val rand = random.nextLong(100000,999999).toInt()
        npcList.add(NpcInfo(rand,customName,coord))
        favourites.add(NpcInfo(rand,customName,coord))
        println("Custom Location Added.")
        saveNpcData()
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
            val npcs = NpcQuery.newQuery().option("Attack").results() + NpcQuery.newQuery().option("Talk to").results()
            npcs.filterNot { isPlayerFamiliar(it) }.forEach { npc ->
                val coord = npc.coordinate
                if (coord != null) {
                    val npcInfo = NpcInfo(npc.configType?.id ?: 0, npc.configType?.name ?: "Unknown", coord)
                    if (npcInfo.name.isNotEmpty() && npcInfo.coordinate.x != 0 && npcInfo.coordinate.y != 0) {
                        npcList.removeAll { it.id == npcInfo.id } // Remove any existing NPC with the same ID
                        ///println("Adding new NPC: $npcInfo")
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
                ///botState = BotState.IDLE
            } else {
                println("Reached NPC")
            }
        } catch (e: Exception) {
            println("Error walking to coordinate: ${e.message}")
            ///botState = BotState.IDLE
        }
    }

    object QuestObjects {
        val questStartLocation: Coordinate = Coordinate(2983,3341,2)
        val questStartNPC: NpcQuery = NpcQuery.newQuery().name("Adrasteia")
        val questAnneDimitriLocation: Coordinate = Coordinate(2970,3339,1)
        val questAnneDimitri: NpcQuery = NpcQuery.newQuery().name("Anne Dimitri")
        val questAnneDimitriLocation2: Coordinate = Coordinate(3138,3533,0)
        val questPointOfInterest: NpcQuery = NpcQuery.newQuery().name("Point of interest")
        val questAnneDimitriLocation3: Coordinate = Coordinate(14382,893,0)
        val questMemoryFragment: NpcQuery = NpcQuery.newQuery().name("Memory fragment")
        val questRestoredMemory: NpcQuery = NpcQuery.newQuery().name("Restored Memory")
        val questEnakhra: NpcQuery = NpcQuery.newQuery().name("Enakhra").mark()
        val questGeneralKhazard: NpcQuery = NpcQuery.newQuery().name("General Khazard").mark()
        val questZemouregal: NpcQuery = NpcQuery.newQuery().name("Zemouregal").mark()
        val questBilrach: NpcQuery = NpcQuery.newQuery().name("Bilrach").mark()
        val questChaosDemon: NpcQuery by lazy { NpcQuery.newQuery().name(enemiesPattern).option("Attack") }
        val questWoundedCultist: NpcQuery = NpcQuery.newQuery().name("Wounded cultist").mark()
        val questAvaryss: NpcQuery = NpcQuery.newQuery().name("Avaryss, the Unceasing")
        val questMoia: NpcQuery = NpcQuery.newQuery().name("Moia")
        val questTrindine: NpcQuery = NpcQuery.newQuery().name("Trindine")
        private val enemiesPattern: Pattern =  Regex.getPatternForContainingOneOf("Chaos demon", "Zamorakian cultist", "Chaos witch")
    }


    fun daughtersOfChaosQuest() {
        val value = VarManager.getVarbitValue(51682)

        if (value == 0 && !Dialog.isOpen() && !Interfaces.isOpen(1500)) {
            walkQueue.add(questStartLocation)
            processWalkQueue()

            questStartNPC.results().first()?.interact("Talk to")
            delay(2000)
            return
        }

        if (value == 0 && Dialog.isOpen() && !Interfaces.isOpen(1500)) {
            Dialog.select()
            delay(200)
            return
        }

        if (value == 0 && Interfaces.isOpen(1500)) {
            MiniMenu.interact(ComponentAction.COMPONENT.type, 1, -1, 98304409)
            return
        }

        if (value == 5) {
            KeyboardInput.pressKey(27)
            delay(2000)
            return
        }

        if ((value == 10 || value == 15) && Dialog.isOpen()) {
            if (Dialog.hasOption("I'll see it done.")) {
                delay(1000)
                Dialog.interact("I'll see it done.")
                delay(1000)
            } else if (Dialog.hasOption("Agree with Adrasteia.")) {
                delay(1000)
                Dialog.interact("Agree with Adrasteia.")
                delay(1000)
            } else {
                Dialog.select()
                delay(200)
            }
            return
        }

        if (value == 15 && !Dialog.isOpen()) {
            walkQueue.add(questAnneDimitriLocation)
            processWalkQueue()
            questAnneDimitri.results().first()?.interact("Talk to")
            delay(2000)
            return
        }

        if (value == 20 && !Dialog.isOpen()) {
            walkQueue.add(questAnneDimitriLocation2)
            processWalkQueue()
            questAnneDimitri.results().first()?.interact("Talk to")
            delay(2000)
            return
        }

        if (value == 20 && Dialog.isOpen()) {
            Dialog.select()
            delay(200)
            return
        }

        if (value == 25 && Dialog.isOpen()) {
            if (Dialog.hasOption("I'm ready.")) {
                delay(1000)
                Dialog.interact("I'm ready.")
                delay(1000)
                return
            } else {
                Dialog.select()
                delay(200)
                return
            }
        }

        if (value == 30 && Dialog.isOpen()) {
            Dialog.select()
            delay(200)
            return
        }

        if (value == 40 && !Dialog.isOpen()) {
            questAnneDimitri.results().first()?.interact("Talk to")
            delay(2000)
            return
        }

        if (value == 40 && Dialog.isOpen()) {
            Dialog.select()
            delay(200)
            return
        }

        if (value == 45) {
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questPointOfInterest.results().firstOrNull() != null) {
                questPointOfInterest.results().first()?.interact("Inspect")
                return
            } else {
                walkQueue.add(questAnneDimitriLocation3)
                processWalkQueue()
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
        }

        if (value == 50) {
            val timer = System.currentTimeMillis().toInt()
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questMemoryFragment.results().firstOrNull() != null) {
                questMemoryFragment.results().first()?.interact("Capture")
                captureTimer = System.currentTimeMillis().toInt()
                return
            }
            if(timer - captureTimer > 5000) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
        }

        if (value == 55) {
            if(!Dialog.isOpen()) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
        }

        if (value == 60) {
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questRestoredMemory.results().firstOrNull() != null) {
                questRestoredMemory.results().first()?.interact("Interact")
                return
            }
        }

        if (value == 65) {
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(!Dialog.isOpen() && counterTalkingPeople == 0) {
                questEnakhra.results().first()?.interact("Talk to")
                delay(5000)
                counterTalkingPeople = 1
                return
            }
            if(!Dialog.isOpen() && counterTalkingPeople == 1) {
                questGeneralKhazard.results().first()?.interact("Talk to")
                delay(5000)
                counterTalkingPeople = 2
                return
            }
            if(!Dialog.isOpen() && counterTalkingPeople == 2) {
                questZemouregal.results().first()?.interact("Talk to")
                delay(5000)
                counterTalkingPeople = 3
                return
            }
            if(!Dialog.isOpen() && counterTalkingPeople == 3) {
                questBilrach.results().first()?.interact("Talk to")
                delay(5000)
                counterTalkingPeople = 4
                return
            }
        }

        if (value == 70 || value == 125 || value == 180) {
            if(LocalPlayer.LOCAL_PLAYER.coordinate == Coordinate(3137,3534,0) || LocalPlayer.LOCAL_PLAYER.coordinate == Coordinate(3136,3534,0)) {
                if(!Dialog.isOpen()) {
                    questAnneDimitri.results().first()?.interact("Talk to")
                    delay(2000)
                    return
                }
                if (Interfaces.isOpen(1188)) {
                    MiniMenu.interact(ComponentAction.DIALOGUE.type, 0, -1, 77856776)
                    MiniMenu.interact(ComponentAction.DIALOGUE.type, 0, -1, 77856781)
                    return
                }
                if(Dialog.isOpen()) {
                    Dialog.select()
                    delay(200)
                    return
                }
            }
            if(!LocalPlayer.LOCAL_PLAYER.inCombat()) {
                if(!Dialog.isOpen() && questAvaryss.results().first()?.animationId == -1) {
                    questAvaryss.results().first()?.interact("Talk to")
                    return
                }
                if(Dialog.isOpen()) {
                    Dialog.select()
                    delay(200)
                    return
                }

                questChaosDemon.results().nearest()?.interact("Attack")
                delay(1000)
                MiniMenu.interact(ComponentAction.COMPONENT.type, 1, -1, 68550678) //3
                delay(500)
                MiniMenu.interact(ComponentAction.COMPONENT.type, 1, -1, 68550673) // 2
                delay(500)
                MiniMenu.interact(SelectableAction.SELECTABLE_COMPONENT.type, 1, -1, 68550668)  //1
                delay(500)
                questChaosDemon.results().nearest()?.interact("Vault")
                delay(500)
                return
            }
            return
        }

        if (value == 75) {
            if(!Dialog.isOpen()) {
                questBilrach.results().first()?.interact("Talk to")
                delay(5000)
                return
            }
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            return
        }

        if (value == 80) {
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }

            if(!Dialog.isOpen()) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            return
        }

        if (value == 85) {
            if(Dialog.isOpen()) {
                if (Dialog.hasOption("We should move on.")) {
                    MiniMenu.interact(ComponentAction.DIALOGUE.type, 0, -1, 77856791)
                    return
                } else {
                    Dialog.select()
                    delay(200)
                    return
                }
            }
            if(!Dialog.isOpen()) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            return
        }

        if (value == 95) {
            if(!Dialog.isOpen()) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            return
        }

        if (value == 100) {
            val timer = System.currentTimeMillis().toInt()
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questPointOfInterest.results().firstOrNull() != null) {
                questPointOfInterest.results().first()?.interact("Inspect")
                captureTimer = System.currentTimeMillis().toInt()
                return
            } else {
                if(timer - captureTimer < 20000) {
                    Movement.walkTo(LocalPlayer.LOCAL_PLAYER.coordinate.x, LocalPlayer.LOCAL_PLAYER.coordinate.y - 20, false)
                    delay(5000)
                    return
                }
                if(timer - captureTimer > 20000) {
                    Movement.walkTo(LocalPlayer.LOCAL_PLAYER.coordinate.x, LocalPlayer.LOCAL_PLAYER.coordinate.y+20, false)
                    questAnneDimitri.results().first()?.interact("Talk to")
                    delay(5000)
                    return
                }
                return
            }
        }

        if (value == 105) {
            val timer = System.currentTimeMillis().toInt()
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questMemoryFragment.results().firstOrNull() != null) {
                questMemoryFragment.results().first()?.interact("Capture")
                captureTimer = System.currentTimeMillis().toInt()
                return
            }
            if(timer - captureTimer > 5000) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
        }

        if (value == 110 || value == 115 || value == 170) {
            if (Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questRestoredMemory.results().firstOrNull() != null) {
                questRestoredMemory.results().first()?.interact("Interact")
                return
            }
        }

        if (value == 120) {
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            return
        }

        if (value == 130) {
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(questWoundedCultist.results().firstOrNull() != null) {
                questWoundedCultist.results().first()?.interact("Talk to")
                return
            } else {
                Movement.walkTo(LocalPlayer.LOCAL_PLAYER.coordinate.x-10,LocalPlayer.LOCAL_PLAYER.coordinate.y,false)
                return
            }
        }

        if (value == 135 || value == 140 || value == 145) {
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(!Dialog.isOpen()) {
                questStartNPC.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            return
        }

        if (value == 150 || value == 155 || value == 165 || value == 185) {
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(!Dialog.isOpen()) {
                questAnneDimitri.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            return
        }

        if(value == 175) {
            if(Dialog.isOpen()) {
                Dialog.select()
                delay(200)
                return
            }
            if(!Dialog.isOpen()) {
                questAvaryss.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            return
        }

        if(value == 190 || value == 195) {
            if(!Dialog.isOpen()) {
                questMoia.results().first()?.interact("Talk to")
                delay(2000)
                return
            }
            if (Dialog.hasOption("Adrasteia sent me.")) {
                delay(1000)
                Dialog.interact("Adrasteia sent me.")
                delay(1000)
                return
            } else if (Dialog.hasOption("Respond diplomatically.")) {
                delay(1000)
                Dialog.interact("Respond diplomatically.")
                delay(1000)
                return
            } else {
                Dialog.select()
                delay(200)
                return
            }
        }

        if(value == 200 || value == 205) {
            if(Dialog.isOpen()) {
                if ((Dialog.hasOption("What do I need to do again?") || Dialog.hasOption("I'll report back to Adrasteia.")) && !talkedToTrindine) {
                    talkedToTrindine = true
                    delay(1000)
                    Dialog.interact("What do I need to do again?")
                    Dialog.interact("I'll report back to Adrasteia.")
                    delay(1000)
                    return
                } else {
                    Dialog.select()
                    delay(200)
                    return
                }
            }
            if(!Dialog.isOpen()) {
                if(!talkedToTrindine) {
                    questTrindine.results().first()?.interact("Talk to")
                    delay(2000)
                    return
                } else {
                    if(questStartNPC.results().nearest() == null) {
                        walkQueue.add(questStartLocation)
                        processWalkQueue()
                        questStartNPC.results().first()?.interact("Talk to")
                        delay(2000)
                        return
                    }
                    return
                }
            }
            return
        }
    return
    }
}
