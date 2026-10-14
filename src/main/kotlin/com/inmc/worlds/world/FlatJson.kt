package com.inmc.worlds.world

/**
 * 평지 설정 → `WorldCreator.generatorSettings` 의 JSON(바닐라 `FlatLevelGeneratorSettings` 모양 — 고전 평지 프리셋과 같다).
 * **서버 없이 돈다**(시험 `FlatJsonTest`).
 *
 * 구조물을 켜면 마을·요새(고전 평지와 같다), 끄면 빈 목록 — 끈 평지에서 마을이 생기지 않게.
 */
object FlatJson {

    /** 오버월드 높이(−64 ~ 320). 층 두께가 이것을 넘으면 맨 위가 잘린다. */
    const val MAX_THICKNESS = 384

    fun of(flat: Flat, structures: Boolean): String = buildString {
        append("{\"layers\":[")
        flat.layers.forEachIndexed { i, layer ->
            if (i > 0) append(',')
            append("{\"block\":").append(quote(layer.block)).append(",\"height\":").append(layer.height.coerceAtLeast(1)).append('}')
        }
        append("],\"biome\":").append(quote(flat.biome))
        append(",\"features\":").append(flat.features)
        append(",\"lakes\":").append(flat.lakes)
        append(",\"structure_overrides\":")
        append(if (structures) "[\"minecraft:villages\",\"minecraft:strongholds\"]" else "[]")
        append('}')
    }

    private fun quote(text: String): String = buildString {
        append('"')
        for (c in text) when (c) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            else -> append(c)
        }
        append('"')
    }
}
