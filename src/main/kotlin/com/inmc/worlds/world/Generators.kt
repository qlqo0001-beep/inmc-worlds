package com.inmc.worlds.world

import org.bukkit.block.Biome
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo

/** 아무것도 만들지 않는 생성기 — "공허" 유형. 스폰 발판은 만들 때 한 번 깐다([WorldService.create]). */
object VoidGenerator : ChunkGenerator() {
    override fun shouldGenerateNoise(): Boolean = false
    override fun shouldGenerateSurface(): Boolean = false
    override fun shouldGenerateCaves(): Boolean = false
    override fun shouldGenerateDecorations(): Boolean = false
    override fun shouldGenerateMobs(): Boolean = false
    override fun shouldGenerateStructures(): Boolean = false
}

/** 생물군계 하나뿐인 월드 — "단일 생물 군계" 유형. 불러올 때마다 같은 것을 다시 건다(저장된 것은 생물군계 id). */
class SingleBiome(private val biome: Biome) : BiomeProvider() {
    override fun getBiome(worldInfo: WorldInfo, x: Int, y: Int, z: Int): Biome = biome
    override fun getBiomes(worldInfo: WorldInfo): List<Biome> = listOf(biome)
}
