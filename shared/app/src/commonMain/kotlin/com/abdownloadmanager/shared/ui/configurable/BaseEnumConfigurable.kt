package com.abdownloadmanager.shared.ui.configurable

import ir.amirab.util.compose.StringSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

abstract class BaseEnumConfigurable<T>(
    title: StringSource,
    description: StringSource,
    backedBy: MutableStateFlow<T>,
    describe: ((T) -> StringSource),
    /**
     * open so a configurable whose options are only known later, such as the video qualities that
     * arrive with the yt-dlp metadata, can expose them live. the default is a construction time
     * snapshot, which for those configurables would stay empty for the rest of the session and make
     * [validate] reject every value the user picks.
     */
    open val possibleValues: List<T>,
    val valueToString: (T) -> List<String>,
    enabled: StateFlow<Boolean> = DefaultEnabledValue,
    visible: StateFlow<Boolean> = DefaultVisibleValue,
) : Configurable<T>(
    title = title,
    description = description,
    backedBy = backedBy,
    describe = describe,
    enabled = enabled,
    visible = visible,
) {
    /**
     * reads the property on every call, not the construction time list, so an override that fills
     * the values in later is the one that decides. without this the renderer would list qualities
     * that [Configurable.set] then refuses, and the picker would look broken.
     */
    override val validate: (T) -> Boolean
        get() = { value -> value in possibleValues }
}
