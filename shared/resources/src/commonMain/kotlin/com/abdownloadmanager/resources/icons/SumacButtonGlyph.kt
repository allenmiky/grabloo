package com.abdownloadmanager.resources.icons

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.PathBuilder

/**
 * Metrics of the window button artwork in the "Sumac Night Molten Glass Blur" Aurorae window
 * decoration theme, measured from the SVGs that ship with it.
 *
 * Every button in that theme is drawn on a [BoxSize] x [BoxSize] box holding a circle of radius
 * [CircleRadius] centred on ([CircleCenter], [CircleCenter]). The icons in this package reuse that
 * exact box, so a glyph can be dropped straight onto a traffic light of the same proportion and
 * [BoxSizeForCircle] is the box size needed to line up with a circle of a given diameter.
 *
 * Each glyph is kept as the SVG path data it was transcribed from rather than as a list of path
 * builder calls, so the numbers stay diffable against the source files and can be measured by the
 * tests. [append], [boundsOf] and [subPathBoundsOf] read that path data; only absolute `M`, `L`,
 * `C` and `Z` are supported, which is all the source artwork uses.
 */
object SumacButtonGlyph {
    /** `ButtonWidth` / `ButtonHeight` in `sumac-night-molten-glass-blurrc`. */
    const val BoxSize = 16f

    /** `r` of the `buttonBase` circle, i.e. `6.8499999` in the SVGs. */
    const val CircleRadius = 6.85f

    const val CircleCenter = BoxSize / 2f

    /** `stroke-width` of the two 45 degree round capped strokes forming the close glyph. */
    const val CloseStrokeWidth = 1.63636f

    /** `stroke-width` of the single round capped stroke forming the minimize glyph. */
    const val MinimizeStrokeWidth = 2f

    /**
     * The 16 unit box scaled so that the circle it contains ends up [circleDiameter] wide.
     *
     * The glyphs are positioned relative to the whole box, not the circle, so a caller that knows
     * the diameter of its own circle needs the matching box size to keep the artwork in
     * proportion.
     */
    fun BoxSizeForCircle(circleDiameter: Float): Float =
        circleDiameter * (BoxSize / (CircleRadius * 2f))

    /** Appends [pathData] to this builder. Every icon in this package draws its artwork like this. */
    fun PathBuilder.append(pathData: String) {
        pathData.forEachSegment { command, points ->
            when (command) {
                'M' -> moveTo(points[0], points[1])
                'L' -> lineTo(points[0], points[1])
                'C' -> curveTo(
                    points[0],
                    points[1],
                    points[2],
                    points[3],
                    points[4],
                    points[5],
                )

                'Z' -> close()
                else -> error("${command.describe()} is not a command these glyphs use")
            }
        }
    }

    /**
     * The bounding box of each subpath in [pathData], in the order they appear.
     */
    fun subPathBoundsOf(pathData: String): List<Rect> {
        val boxes = mutableListOf<Rect>()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var currentX = 0f
        var currentY = 0f

        fun record(x: Float, y: Float) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }

        fun flush() {
            if (minX <= maxX) {
                boxes += Rect(minX, minY, maxX, maxY)
            }
            minX = Float.MAX_VALUE
            minY = Float.MAX_VALUE
            maxX = -Float.MAX_VALUE
            maxY = -Float.MAX_VALUE
        }

        pathData.forEachSegment { command, points ->
            when (command) {
                'M' -> {
                    flush()
                    currentX = points[0]
                    currentY = points[1]
                    record(currentX, currentY)
                }

                'L' -> {
                    currentX = points[0]
                    currentY = points[1]
                    record(currentX, currentY)
                }

                'C' -> {
                    val fromX = currentX
                    val fromY = currentY
                    val x1 = points[0]
                    val y1 = points[1]
                    val x2 = points[2]
                    val y2 = points[3]
                    val x3 = points[4]
                    val y3 = points[5]
                    for (step in 0..CURVE_STEPS) {
                        val t = step / CURVE_STEPS.toFloat()
                        val inverse = 1f - t
                        record(
                            inverse * inverse * inverse * fromX +
                                3f * inverse * inverse * t * x1 +
                                3f * inverse * t * t * x2 +
                                t * t * t * x3,
                            inverse * inverse * inverse * fromY +
                                3f * inverse * inverse * t * y1 +
                                3f * inverse * t * t * y2 +
                                t * t * t * y3,
                        )
                    }
                    currentX = x3
                    currentY = y3
                }

                'Z' -> flush()
                else -> error("${command.describe()} is not a command these glyphs use")
            }
        }
        flush()
        return boxes
    }

    /** The bounding box of everything [pathData] draws. */
    fun boundsOf(pathData: String): Rect {
        val boxes = subPathBoundsOf(pathData)
        require(boxes.isNotEmpty()) { "path data has no segments" }
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        boxes.forEach { box ->
            if (box.left < left) left = box.left
            if (box.top < top) top = box.top
            if (box.right > right) right = box.right
            if (box.bottom > bottom) bottom = box.bottom
        }
        return Rect(left, top, right, bottom)
    }

    /**
     * The box a round capped stroke of [strokeWidth] actually paints, which is the outline grown by
     * half the stroke width on every side.
     */
    fun strokedBoundsOf(pathData: String, strokeWidth: Float): Rect {
        val outline = boundsOf(pathData)
        val grow = strokeWidth / 2f
        return Rect(
            outline.left - grow,
            outline.top - grow,
            outline.right + grow,
            outline.bottom + grow,
        )
    }

    private const val CURVE_STEPS = 48
    private val SUPPORTED_COMMANDS = setOf('M', 'L', 'C', 'Z')
    private val tokenPattern = Regex("([A-Za-z])|(-?\\d*\\.?\\d+)")

    private fun Any.describe(): String = if (this is Char) "'$this'" else toString()

    private inline fun String.forEachSegment(block: (Char, FloatArray) -> Unit) {
        val tokens = tokenPattern.findAll(this).map { match ->
            val command = match.groupValues[1]
            if (command.isEmpty()) match.groupValues[2].toFloat() else command.first()
        }.toList()
        require(tokens.isNotEmpty()) { "path data is empty" }

        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            require(token is Char && token in SUPPORTED_COMMANDS) {
                "${token.describe()} is not a command these glyphs use"
            }
            val command = token
            index++

            if (command == 'Z') {
                block(command, FloatArray(0))
                continue
            }

            val argumentCount = argumentsPerCommand(command)
            require(index + argumentCount <= tokens.size) { "'$command' is missing coordinates" }
            val coordinates = FloatArray(argumentCount)
            for (slot in coordinates.indices) {
                val value = tokens[index + slot]
                require(value is Float) {
                    "'$command' got ${value.describe()} where a number belongs"
                }
                coordinates[slot] = value
            }
            index += argumentCount
            block(command, coordinates)
        }
    }

    private fun argumentsPerCommand(command: Char): Int = when (command) {
        'M', 'L' -> 2
        'C' -> 6
        else -> error("${command.describe()} is not a command these glyphs use")
    }
}