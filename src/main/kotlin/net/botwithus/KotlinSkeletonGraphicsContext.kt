package net.botwithus

import net.botwithus.rs3.imgui.ImGui
import net.botwithus.rs3.imgui.ImGuiWindowFlag
import net.botwithus.rs3.script.ScriptConsole
import net.botwithus.rs3.script.ScriptGraphicsContext

class KotlinSkeletonGraphicsContext(
    val script: KotlinSkeleton,
    console: ScriptConsole
) : ScriptGraphicsContext(console) {

    var filteredNpcs: MutableList<KotlinSkeleton.NpcInfo> = mutableListOf()
    private var favourites: MutableList<KotlinSkeleton.NpcInfo> = mutableListOf()

    init {
        // Initialize filteredNpcs with all NPCs initially
        filteredNpcs.addAll(script.npcList)
        favourites.addAll(script.favourites)
        script.selectedNpc = filteredNpcs.first()
        script.selectedNpc2 = favourites.first()
    }

    override fun drawSettings() {
        super.drawSettings()

        ImGui.SetWindowSize(415f, 403f)

        // Main window for the script
        if (ImGui.Begin("My script", ImGuiWindowFlag.None.value)) {

            if (ImGui.BeginTabBar("My bar", ImGuiWindowFlag.None.value)) {

                // Settings tab
                if (ImGui.BeginTabItem("Settings", ImGuiWindowFlag.None.value)) {
                    drawSettingsTab()
                    ImGui.EndTabItem()
                }

                // NPC Navigation tab
                if (ImGui.BeginTabItem("NPC Navigation", ImGuiWindowFlag.None.value)) {
                    drawNpcNavigationTab()
                    ImGui.EndTabItem()
                }

                // Favourites tab
                if (ImGui.BeginTabItem("Favourites", ImGuiWindowFlag.None.value)) {
                    drawFavouritesTab()
                    ImGui.EndTabItem()
                }

                ImGui.EndTabBar()
            }
            ImGui.End()
        }
    }

    private fun drawSettingsTab() {
        ImGui.Text("Welcome to my script!")
        ImGui.Text("My script's state is: ${script.botState}")
    }

    private fun drawNpcNavigationTab() {
        script.searchNpc = ImGui.InputText("Search", script.searchNpc, 256, ImGuiWindowFlag.None.value)

        ImGui.SameLine()
        if (ImGui.Button("Refresh")) {
            filteredNpcs.clear()
            filteredNpcs.addAll(script.npcList)
        }

        ImGui.BeginChild("npcList", 0f, 256f, true, 256)
        filteredNpcs.filter { it.name.contains(script.searchNpc, ignoreCase = true) }.forEach { npc ->
            if (ImGui.Selectable("${npc.name} (${npc.id})", false, ImGuiWindowFlag.None.value)) {
                script.selectedNpc = npc
            }
        }
        ImGui.EndChild()

        script.selectedNpc.let { npc ->
            if (ImGui.Button("Go to ${npc.name}")) {
                script.requestWalkTo(npc.coordinate)
            }
            ImGui.SameLine()
            if (ImGui.Button("Add to Favourites ${npc.name}")) {
                if (!script.isNpcInFavourites(npc)) {
                    script.addToFavourites(npc)
                    favourites = script.favourites.toMutableList()
                }
            }
        }
    }

    private fun drawFavouritesTab() {
        script.searchNpc2 = ImGui.InputText("Search", script.searchNpc2, 256, ImGuiWindowFlag.None.value)

        ImGui.BeginChild("npcList2", 0f, 256f, true, 256)
        favourites.filter { it.name.contains(script.searchNpc2, ignoreCase = true) }.forEach { npc ->
            if (ImGui.Selectable("${npc.name} (${npc.id})", false, ImGuiWindowFlag.None.value)) {
                script.selectedNpc2 = npc
            }
        }
        ImGui.EndChild()

        script.selectedNpc2.let { npc ->
            if (ImGui.Button("Go to ${npc.name}")) {
                script.requestWalkTo(npc.coordinate)
            }

            ImGui.SameLine()
            if (ImGui.Button("Remove from favourites ${npc.name}")) {
                if (script.isNpcInFavourites(npc)) {
                    script.removeFromFavourites(npc)
                    favourites = script.favourites.toMutableList()
                }
            }
        }
    }

    override fun drawOverlay() {
        super.drawOverlay()
        // Optionally draw overlay elements here if needed
    }
}
