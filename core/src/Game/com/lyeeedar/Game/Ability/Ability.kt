package com.lyeeedar.Game.Ability

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Array
import com.badlogic.gdx.utils.ObjectFloatMap
import com.badlogic.gdx.utils.ObjectMap
import com.lyeeedar.Board.DelayedAction
import com.lyeeedar.Board.Grid
import com.lyeeedar.Board.Spreader
import com.lyeeedar.Board.Tile
import com.lyeeedar.Game.Buff
import com.lyeeedar.Game.Global
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Screens.GridScreen
import com.lyeeedar.Statistic
import com.lyeeedar.UI.GridWidget
import com.lyeeedar.UI.PowerBar
import com.lyeeedar.UI.Seperator
import com.lyeeedar.Util.*
import ktx.collections.set
import ktx.collections.toGdxArray

/**
 * Created by Philip on 20-Jul-16.
 */

class Ability
{
	lateinit var name: String
	lateinit var description: String

	var hitEffect: ParticleEffect? = null
	var flightEffect: ParticleEffect? = null

	var cost: Int = 2

	var maxUsages: Int = -1
	var remainingUsages: Int = -1
	var resetUsagesPerLevel = false

	var targets = 1
	var targetter: Targetter = Targetter(Targetter.Type.ORB)
	var permuter: Permuter = Permuter(Permuter.Type.SINGLE)
	var effect: Effect = Effect(Effect.Type.TEST)
	val data = ObjectMap<String, Any>()

	val selectedTargets = Array<Tile>()

	fun createTable(): Table
	{
		val table = Table()
		table.defaults().pad(5f)

		val titleStack = Stack()
		val iconTable = Table()
		titleStack.add(iconTable)
		titleStack.add(Label(name, Statics.skin, "cardtitle"))

		table.add(titleStack).growX()
		table.row()

		val descLabel = Label(description, Statics.skin, "card")
		descLabel.setWrap(true)

		table.add(descLabel).growX()
		table.row()

		table.add(Seperator(Statics.skin, "horizontalcard")).growX().pad(10f)
		table.row()

		if (effect.type == Effect.Type.BUFF)
		{
			val buff = data["BUFF"] as Buff

			var turns = buff.remainingDuration.toString()

			if (Global.player.getStat(Statistic.BUFFDURATION, true) > 0f)
			{
				val bonus = (buff.remainingDuration.toFloat() * Global.player.getStat(Statistic.BUFFDURATION, true)).toInt()
				turns = "($turns + $bonus)"
			}

			val effectDesc = "For $turns turns gain buff:"

			val effectLabel = Label(effectDesc, Statics.skin, "card")
			effectLabel.setWrap(true)

			table.add(effectLabel).growX()
			table.row()

			table.add(Seperator(Statics.skin)).growX()
			table.row()

			table.add(buff.createTable(false)).growX()
			table.row()

			table.add(Seperator(Statics.skin)).growX()
			table.row()
		}
		else
		{
			var effectDesc = "Target $targets " + targetter.type.toString().toLowerCase().capitalize().pluralize(targets)

			if (permuter.type != Permuter.Type.SINGLE)
			{
				effectDesc += " then " + permuter.toString(data)
			}

			val them = if (targets > 1 || permuter.type != Permuter.Type.SINGLE) "them" else "it"
			effectDesc += " and " + effect.toString(data, them, targetter.popAction(), this)

			val effectLabel = Label(effectDesc, Statics.skin, "card")
			effectLabel.setWrap(true)

			table.add(effectLabel).growX()
			table.row()
		}

		table.add(Label("Cost: $cost", Statics.skin, "card")).growX()
		table.row()

		if (maxUsages > 0)
		{
			if (resetUsagesPerLevel)
			{
				table.add(Label("Usages Per Encounter: $maxUsages", Statics.skin, "card")).growX()
				table.row()
			}
			else
			{
				table.add(Label("Remaining Usages: $remainingUsages", Statics.skin, "card")).growX()
				table.row()
			}
		}

		return table
	}

	fun activate(grid: Grid)
	{
		if (maxUsages > 0)
		{
			if (remainingUsages == 0)
			{
				return
			}

			remainingUsages--
		}

		PowerBar.instance.pips -= cost

		if (effect.type == Effect.Type.BUFF)
		{
			effect.apply(Tile(0, 0), grid, 0f, data, Array(), ObjectFloatMap())
			return
		}

		val finalTargets = Array<Tile>()

		if (permuter.type == Permuter.Type.RANDOM && targets == 0)
		{
			for (t in permuter.permute(grid.tile(grid.width/2, grid.height/2)!!, grid, data, selectedTargets, this, null))
			{
				if (!selectedTargets.contains(t, true))
				{
					selectedTargets.add(t)
				}
			}
		}

		val selectedDelays = ObjectMap<Tile, Float>()

		for (target in selectedTargets)
		{
			if (permuter.type == Permuter.Type.RANDOM && targets == 0)
			{
				finalTargets.add(target)
			}
			else
			{
				for (t in permuter.permute(target, grid, data, selectedTargets, this, null))
				{
					if (!finalTargets.contains(t, true))
					{
						finalTargets.add(t)
					}
				}

				val coverage = data["COVERAGE", "1"]?.toString()?.toFloat() ?: 1f
				if (coverage < 1f)
				{
					val chosenCount = (finalTargets.size.toFloat() * coverage).ciel()
					while (finalTargets.size > chosenCount)
					{
						finalTargets.removeRandom(Random.random)
					}
				}
			}

			var delay = 0f
			if (flightEffect != null)
			{
				val fs = flightEffect!!.copy()
				fs.killOnAnimComplete = true

				val p1 = Vector2(Statics.stage.width / 2f, 0f)
				val p2 = GridWidget.instance.pointToScreenspace(target)

				val gridWidget = GridScreen.instance.grid!!
				p1.scl(1f / gridWidget.renderer.tileSize)
				p2.scl(1f / gridWidget.renderer.tileSize)

				val dist = p1.dst(p2)

				fs.animation = MoveAnimation.obtain().set((0.25f + dist * 0.025f) * (1.0f / fs.timeMultiplier), arrayOf(p1, p2), Interpolation.linear)

				fs.rotation = getRotation(p1, p2)
				delay += fs.lifetime

				target.effects.add(fs)
			}

			selectedDelays[target] = delay
		}

		// make variables map
		val variables = ObjectFloatMap<String>()
		for (stat in Statistic.Values)
		{
			variables[stat.toString().toUpperCase()] = Global.player.getStat(stat, true)
		}
		val monsters = finalTargets.mapNotNull { it.monster }.toGdxArray()
		variables["MONSTERCOUNT"] = monsters.size.toFloat()
		variables["MONSTERHP"] = monsters.map { it.hp }.sum()
		variables["TILECOUNT"] = finalTargets.filter { it.canHaveOrb }.size.toFloat()

		val originalTargets = selectedTargets.toGdxArray()
		for (target in finalTargets)
		{
			val closest = selectedTargets.minBy { it.dist(target) }!!
			val dst = if (permuter.type == Permuter.Type.RANDOM) 0 else closest.dist(target)

			target.delayedActions.add(DelayedAction(
					{
						var delay = 0.0f
						if (hitEffect != null)
						{
							val hs = hitEffect!!.copy()
							hs.renderDelay = delay + 0.1f * dst
							delay += hs.lifetime * 0.6f

							if (permuter.type == Permuter.Type.BLOCK || permuter.type == Permuter.Type.DIAMOND)
							{
								// single sprite
								if (originalTargets.contains(target, true))
								{
									hs.size[0] = data["AOE"].toString().toInt() * 2 + 1
									hs.size[1] = hs.size[0]
									hs.isCentered = true
									target.effects.add(hs)
								}
							}
							else
							{
								target.effects.add(hs)
							}
						}

						effect.apply(target, grid, delay, data, originalTargets, variables)
					}, selectedDelays[closest] - 0.05f))

		}

		selectedTargets.clear()
	}

	fun hasValidTargets(grid: Grid): Boolean
	{
		if (maxUsages > 0 && remainingUsages == 0)
		{
			return false
		}
		else if (effect.type == Effect.Type.BUFF)
		{
			val buff = data["BUFF"] as Buff
			if (Global.player.levelbuffs.any { it.name == buff.name && it.remainingDuration > 1 })
			{
				return false
			}

			return true
		}
		else
		{
			return getValidTargets(grid).size > 0
		}
	}

	fun getValidTargets(grid: Grid): Array<Point>
	{
		val output = Array<Point>()

		for (tile in grid.grid)
		{
			if (targetter.isValid(tile, data))
			{
				output.add(tile)
			}
		}

		return output
	}

	fun parse(xml: XmlData)
	{
		name = xml.get("Name")
		description = xml.get("Description")

		val dataEl = xml.getChildByName("EffectData")!!

		cost = dataEl.getInt("Cost", 1)

		maxUsages = dataEl.getInt("Usages", -1)
		remainingUsages = maxUsages
		resetUsagesPerLevel = dataEl.getBoolean("ResetUsagesPerLevel", false)

		val effectDesc = dataEl.get("Effect")
		val split = effectDesc.toUpperCase().split(",")

		targets = split[0].toInt()
		targetter = Targetter(Targetter.Type.valueOf(split[1]))
		permuter = Permuter(Permuter.Type.valueOf(split[2]))
		effect = Effect(Effect.Type.valueOf(split[3]))

		val dEl = dataEl.getChildByName("Data")
		if (dEl != null)
		{
			for (el in dEl.children)
			{
				if (el.name == "Spreader")
				{
					val spreader = Spreader.load(el)
					data[el.name.toUpperCase()] = spreader

				}
				else if (el.name == "Buff")
				{
					val buff = Buff.load(el)
					data[el.name.toUpperCase()] = buff
				}
				else if (el.name == "Summon")
				{
					data[el.name.toUpperCase()] = el
				}
				else
				{
					data[el.name.toUpperCase()] = el.text.toUpperCase()
				}
			}
		}

		val hitEffectData = dataEl.getChildByName("HitEffect")
		if (hitEffectData != null) hitEffect = AssetManager.loadParticleEffect(hitEffectData).getParticleEffect()
		val flightEffectData = dataEl.getChildByName("FlightEffect")
		if (flightEffectData != null) flightEffect = AssetManager.loadParticleEffect(flightEffectData).getParticleEffect()

		if (effect.type == Effect.Type.BUFF)
		{
			targets = 0
		}
	}

	companion object
	{
		fun load(xml: XmlData): Ability
		{
			val ability = Ability()
			ability.parse(xml)
			return ability
		}
	}
}