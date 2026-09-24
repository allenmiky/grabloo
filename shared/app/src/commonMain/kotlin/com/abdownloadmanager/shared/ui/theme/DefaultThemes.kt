package com.abdownloadmanager.shared.ui.theme

import androidx.compose.ui.graphics.Color
import com.abdownloadmanager.shared.util.ui.MyColors

object DefaultThemes {
    val mojaveDark = MyColors(
        id = "mojave_dark",
        name = "Mojave Dark",
        primary = Color(0xFF0A84FF),
        primaryVariant = Color(0xFF3B9BFF),
        onPrimary = Color.White,
        secondary = Color(0xFF3B9BFF),
        secondaryVariant = Color(0xFF0A84FF),
        onSecondary = Color.White,
        background = Color(0xFF1E1E1E),
        onBackground = Color(0xFFE8E8EA),
        surface = Color(0xFF252526),
        onSurface = Color(0xFFE8E8EA),
        error = Color(0xFFFF453A),
        onError = Color.White,
        success = Color(0xFF32D74B),
        onSuccess = Color(0xFF0A1E10),
        warning = Color(0xFFFF9F0A),
        onWarning = Color(0xFF241400),
        info = Color(0xFF0A84FF),
        onInfo = Color.White,
        isLight = false,
        panel = Color(0xFF252526),
        sidebar = Color(0xFF2B2B2D),
        toolbar = Color(0xFF323234),
        divider = Color(0xFF3A3A3D),
        dimText = Color(0xFF9A9AA0),
        selectedRow = Color(0xFF0A5FCF),
        selectedRowText = Color(0xFFF2F2F4),
        selectedRowDimText = Color(0xFFF2F2F4),
        alternateRow = Color(0x05FFFFFF),
        secondaryButtonTop = Color(0xFF5A5A5E),
        secondaryButtonBottom = Color(0xFF48484B),
    )

    val graphite = MyColors(
        id = "graphite",
        name = "Graphite",
        primary = Color(0xFF8E8E93),
        primaryVariant = Color(0xFFA6A6AD),
        onPrimary = Color.White,
        secondary = Color(0xFF8E8E93),
        secondaryVariant = Color(0xFF5E5E64),
        onSecondary = Color.White,
        background = Color(0xFF1C1C1E),
        onBackground = Color(0xFFDEDEE0),
        surface = Color(0xFF232325),
        onSurface = Color(0xFFDEDEE0),
        error = Color(0xFFFF453A),
        onError = Color.White,
        success = Color(0xFF32D74B),
        onSuccess = Color(0xFF0A1E10),
        warning = Color(0xFFFF9F0A),
        onWarning = Color(0xFF241400),
        info = Color(0xFF8E8E93),
        onInfo = Color.White,
        isLight = false,
        panel = Color(0xFF232325),
        sidebar = Color(0xFF2A2A2E),
        toolbar = Color(0xFF333338),
        divider = Color(0xFF3A3A3E),
        dimText = Color(0xFF9A9AA0),
        selectedRow = Color(0xFF48484D),
        selectedRowText = Color(0xFFF2F2F4),
        selectedRowDimText = Color(0xFFF2F2F4),
        alternateRow = Color(0x05FFFFFF),
        secondaryButtonTop = Color(0xFF5A5A5E),
        secondaryButtonBottom = Color(0xFF48484B),
    )

    val oceanTeal = MyColors(
        id = "ocean_teal",
        name = "Ocean Teal",
        primary = Color(0xFF30D5C8),
        primaryVariant = Color(0xFF4BE3D8),
        onPrimary = Color(0xFF041412),
        secondary = Color(0xFF30D5C8),
        secondaryVariant = Color(0xFF155E66),
        onSecondary = Color.White,
        background = Color(0xFF141A22),
        onBackground = Color(0xFFDDE7EE),
        surface = Color(0xFF1B2330),
        onSurface = Color(0xFFDDE7EE),
        error = Color(0xFFFF453A),
        onError = Color.White,
        success = Color(0xFF32D74B),
        onSuccess = Color(0xFF0A1E10),
        warning = Color(0xFFFF9F0A),
        onWarning = Color(0xFF241400),
        info = Color(0xFF30D5C8),
        onInfo = Color(0xFF041412),
        isLight = false,
        panel = Color(0xFF1B2330),
        sidebar = Color(0xFF1D2833),
        toolbar = Color(0xFF232E3D),
        divider = Color(0xFF2C3B4D),
        dimText = Color(0xFF9AAFC0),
        selectedRow = Color(0xFF155E66),
        selectedRowText = Color(0xFFEFFBF9),
        selectedRowDimText = Color(0xFFEFFBF9),
        alternateRow = Color(0x05FFFFFF),
        secondaryButtonTop = Color(0xFF3A4A5C),
        secondaryButtonBottom = Color(0xFF2B3847),
    )


    fun getAll(): List<MyColors> {
        return listOf(
            mojaveDark,
            graphite,
            oceanTeal,
        )
    }

    fun getDefaultDark() = mojaveDark
    fun getDefaultLight() = mojaveDark
}
