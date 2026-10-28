package com.lagradost.desktop.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import com.lagradost.desktop.platform.AppRestart
import com.lagradost.desktop.platform.Graphics
import com.lagradost.desktop.platform.LowEnd
import com.lagradost.desktop.platform.PerformanceMode
import com.lagradost.desktop.platform.RenderChoice
import com.lagradost.desktop.ui.fluent.Appearance
import com.lagradost.desktop.ui.fluent.Button
import com.lagradost.desktop.ui.fluent.ButtonKind
import com.lagradost.desktop.ui.fluent.ComboBox

/** Settings > General > Performance: the low-end PC mode and the renderer that draws the window. The lines of a description are translated one by one. */
@Composable
fun GroupBuilder.performanceItems() {
    fun changed() = Appearance.save()
    val state = if (Appearance.light) "The light look is in use now." else "The full look is in use now."
    card(
        "Low-end PC mode",
        "For an old or small PC: shorter animations, no hover zoom or moving profile pictures, no seek preview, no frame blending in the video, smaller picture and texture caches.\n${LowEnd.describe()}\n$state",
        "slow lag laggy old weak low end potato ram memory cpu animations reduce light performance",
    ) {
        ComboBox(PerformanceMode.entries.toList(), Appearance.performance, { it.label }, { Appearance.performance = it; changed() }, minWidth = 170.dp)
    }
    card(
        "Graphics renderer",
        "Which renderer draws the window. If it flickers, flashes or shows black or white patches, try OpenGL, or Software if that does not help. A change applies after a restart.\nIn use now: ${Graphics.api}" + (if (Graphics.fellBack) "\nThe app chose it itself because it did not start properly with the faster ones." else ""),
        "flicker flickering flash black white blank patches glitch gpu driver direct3d d3d opengl software graphics card rendering",
    ) {
        var chosen by remember { mutableStateOf(Graphics.choice()) }
        var restart by remember { mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (restart) Button("Restart now", { AppRestart.request() }, kind = ButtonKind.Standard)
            ComboBox(RenderChoice.entries.toList(), chosen, { it.label }, { chosen = it; Graphics.setChoice(it); restart = true }, minWidth = 170.dp)
        }
    }
}
