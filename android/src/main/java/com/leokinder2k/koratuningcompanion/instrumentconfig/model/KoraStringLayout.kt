package com.leokinder2k.koratuningcompanion.instrumentconfig.model

enum class KoraStringSide {
    LEFT,
    RIGHT
}

data class KoraStringRole(
    val side: KoraStringSide,
    val positionFromLow: Int
)

object KoraStringLayout {
    private data class SideLayout(
        val leftOrder: List<Int>,
        val rightOrder: List<Int>
    ) {
        val roleByStringNumber: Map<Int, KoraStringRole> = buildMap {
            leftOrder.forEachIndexed { index, stringNumber ->
                put(
                    stringNumber,
                    KoraStringRole(
                        side = KoraStringSide.LEFT,
                        positionFromLow = index + 1
                    )
                )
            }
            rightOrder.forEachIndexed { index, stringNumber ->
                put(
                    stringNumber,
                    KoraStringRole(
                        side = KoraStringSide.RIGHT,
                        positionFromLow = index + 1
                    )
                )
            }
        }
    }

    private val predefinedLayouts = mapOf(
        // 21-string, F reference (low -> high on each side):
        // L: F C D E G Bb D F A C E
        // R: F A C E G Bb D F G A
        21 to SideLayout(
            leftOrder = listOf(1, 2, 3, 4, 6, 8, 10, 12, 14, 16, 18),
            rightOrder = listOf(5, 7, 9, 11, 13, 15, 17, 19, 20, 21)
        ),
        // 22-string adds low right Bb (low -> high on each side):
        // L: F C D E G Bb D F A C E
        // R: Bb F A C E G Bb D F G A
        22 to SideLayout(
            leftOrder = listOf(1, 3, 4, 5, 7, 9, 11, 13, 15, 17, 19),
            rightOrder = listOf(2, 6, 8, 10, 12, 14, 16, 18, 20, 21, 22)
        )
    )

    fun leftOrder(stringCount: Int): List<Int> {
        return predefinedLayouts[stringCount]?.leftOrder ?: defaultLeftOrder(stringCount)
    }

    fun rightOrder(stringCount: Int): List<Int> {
        return predefinedLayouts[stringCount]?.rightOrder ?: defaultRightOrder(stringCount)
    }

    fun roleFor(stringCount: Int, stringNumber: Int): KoraStringRole {
        val predefined = predefinedLayouts[stringCount]?.roleByStringNumber?.get(stringNumber)
        if (predefined != null) {
            return predefined
        }

        return if (stringNumber % 2 == 1) {
            KoraStringRole(
                side = KoraStringSide.LEFT,
                positionFromLow = (stringNumber / 2) + 1
            )
        } else {
            KoraStringRole(
                side = KoraStringSide.RIGHT,
                positionFromLow = stringNumber / 2
            )
        }
    }

    private fun defaultLeftOrder(stringCount: Int): List<Int> {
        return (1..stringCount).filter { stringNumber -> stringNumber % 2 == 1 }
    }

    private fun defaultRightOrder(stringCount: Int): List<Int> {
        return (1..stringCount).filter { stringNumber -> stringNumber % 2 == 0 }
    }
}

