package com.lyeeedar.Game.Ability

import com.lyeeedar.Board.Tile
import com.lyeeedar.Board.isMonster
import com.lyeeedar.Components.*
import com.lyeeedar.Util.DataValue
import com.lyeeedar.Util.XmlData
import com.lyeeedar.Util.XmlDataClass

enum class TargetterType
{
	BASICORB,
	ORB,
	SPECIAL,
	BLOCK,
	EMPTY,
	SEALED,
	MONSTER,
	ATTACK,
	TILE,
	SPREADER,
	NAMEDTILE
}

class Targetter : XmlDataClass()
{
	lateinit var type: TargetterType

	@DataValue(visibleIf = "Type == SPREADER")
	var spreaderName: String? = null

	@DataValue(visibleIf = "Type == NAMEDTILE")
	var tileName: String? = null

	override fun load(xmlData: XmlData)
	{

	}
}

fun Targetter.isValid(tile: Tile): Boolean
{
	return when(type)
	{
		TargetterType.BASICORB -> tile.contents?.isBasicOrb() == true && !tile.contents!!.swappable()!!.sealed && tile.spreader == null

		TargetterType.ORB -> (tile.contents?.matchable() != null || tile.contents?.special() != null) && tile.spreader == null

		TargetterType.SPECIAL -> tile.contents?.special() != null && tile.spreader == null

		TargetterType.BLOCK -> tile.contents?.damageable() != null && tile.contents?.ai() == null && tile.spreader == null

		TargetterType.EMPTY -> tile.contents == null && tile.canHaveOrb && tile.spreader == null

		TargetterType.SEALED -> tile.contents?.swappable()?.sealed == true && tile.spreader == null

		TargetterType.MONSTER ->  tile.contents?.isMonster() == true && tile.spreader == null

		TargetterType.ATTACK ->  tile.contents?.monsterEffect() != null && tile.spreader == null

		TargetterType.TILE -> tile.canHaveOrb

		TargetterType.SPREADER ->  tile.spreader?.data?.nameKey == spreaderName

		TargetterType.NAMEDTILE -> tile.nameKey == tileName

		else -> throw Exception("Invalid targetter type $type")
	}
}