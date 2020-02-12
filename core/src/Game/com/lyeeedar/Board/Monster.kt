package com.lyeeedar.Board

import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.utils.Array
import com.lyeeedar.Components.*
import com.lyeeedar.Direction
import com.lyeeedar.Game.Ability.*
import com.lyeeedar.Game.Global
import com.lyeeedar.Renderables.Animation.BumpAnimation
import com.lyeeedar.Renderables.Animation.ExpandAnimation
import com.lyeeedar.Renderables.Animation.LeapAnimation
import com.lyeeedar.Renderables.Animation.MoveAnimation
import com.lyeeedar.Renderables.Particle.ParticleEffect
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Statistic
import com.lyeeedar.UI.Tutorial
import com.lyeeedar.Util.*
import ktx.collections.addAll
import ktx.collections.toGdxArray
import java.util.*

fun Entity.isMonster(): Boolean
{
	if (this.hasComponent(ComponentType.Damageable) && this.ai()?.ai is MonsterAI)
	{
		return true
	}

	return false
}

@DataClass(global = true)
class MonsterDesc : XmlDataClass()
{
	lateinit var name: String
	lateinit var sprite: Sprite
	lateinit var death: ParticleEffect
	var attackNumPips: Int = 5
	var attackCooldown: Point = Point(6, 6)
	var attackDamage: Int = 1
	var size: Int = 1
	var hp: Int = 10
	var damageReduction: Int = 0
	val abilities: Array<AbstractMonsterAbilityData> = Array<AbstractMonsterAbilityData>()
	val stages: Array<MonsterDesc> = Array<MonsterDesc>()

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		name = xmlData.get("Name")
		sprite = AssetManager.loadSprite(xmlData.getChildByName("Sprite")!!)
		death = AssetManager.loadParticleEffect(xmlData.getChildByName("Death")!!).getParticleEffect()
		attackNumPips = xmlData.getInt("AttackNumPips", 5)
		val attackCooldownRaw = xmlData.get("AttackCooldown", "6, 6")!!.split(',')
		attackCooldown = Point(attackCooldownRaw[0].toInt(), attackCooldownRaw[1].toInt())
		attackDamage = xmlData.getInt("AttackDamage", 1)
		size = xmlData.getInt("Size", 1)
		hp = xmlData.getInt("Hp", 10)
		damageReduction = xmlData.getInt("DamageReduction", 0)
		val abilitiesEl = xmlData.getChildByName("Abilities")!!
		for (el in abilitiesEl.children)
		{
			val obj = AbstractMonsterAbilityData.loadPolymorphicClass(el.get("classID"))
			obj.load(el)
			abilities.add(obj)
		}
		val stagesEl = xmlData.getChildByName("Stages")!!
		for (el in stagesEl.children)
		{
			val obj = MonsterDesc()
			obj.load(el)
			stages.add(obj)
		}
	}
}

val monsterBuilder = EntityArchetypeBuilder()
	.add(ComponentType.EntityArchetype)
	.add(ComponentType.Position)
	.add(ComponentType.Renderable)
	.add(ComponentType.AI)
	.add(ComponentType.Damageable)
	.add(ComponentType.Tutorial)

fun MonsterDesc.getEntity(difficulty: Int, isSummon: Boolean, grid: Grid): Entity
{
	val entity = monsterBuilder.build()

	entity.archetype()!!.set(EntityArchetype.MONSTER)

	val position = entity.pos()!!
	position.size = size

	entity.renderable()!!.set(sprite.copy())

	val damageable = entity.damageable()!!
	damageable.deathEffect = death.copy()
	damageable.maxhp = hp + (hp.toFloat() * (difficulty.toFloat() / 7f)).ciel()
	damageable.isSummon = isSummon
	damageable.damageReduction = damageReduction
	damageable.alwaysShowHP = true

	if (difficulty >= 3)
	{
		damageable.damageReduction += (difficulty.toFloat() / 5f).ciel()
	}

	val ai = entity.ai()!!
	ai.ai = MonsterAI(this, difficulty, grid)

	val tutorialComponent = entity.tutorial()!!
	tutorialComponent.displayTutorial = fun (grid, entity, gridWidget): Tutorial? {
		if (damageable.damageReduction > 0  && !Statics.settings.get("DR", false))
		{
			val tutorial = Tutorial("DR")
			tutorial.addPopup(Localisation.getText("monster.dr.tutorial", "UI"), gridWidget.getRect(entity))
			return tutorial
		}

		if (!Statics.settings.get("Monster", false) )
		{
			val tutorial = Tutorial("Monster")
			tutorial.addPopup(Localisation.getText("monster.tutorial", "UI"), gridWidget.getRect(entity))
			return tutorial
		}

		if ((ai.ai as MonsterAI).desc.stages.size > 0 && !Statics.settings.get("MonsterStages", false))
		{
			val tutorial = Tutorial("MonsterStages")
			tutorial.addPopup(Localisation.getText("monster.stages.tutorial", "UI"), gridWidget.getRect(entity))
			return tutorial
		}

		return null
	}

	return entity
}

fun Entity.monsterAI(): MonsterAI? = this.ai()?.ai as? MonsterAI

class MonsterAI(val desc: MonsterDesc, val difficulty: Int, grid: Grid) : AbstractGridAI()
{
	val abilities = Array<AbstractMonsterAbility<*>>()

	var fastAttacks = -1
	var powerfulAttacks = -1

	var atkCooldown = 0
	var attackDamage = 1
	var attackNumPips = 7
	var attackCooldown: Point = Point(6, 6)

	var originalDesc: MonsterDesc = desc

	init
	{
		attackDamage = desc.attackDamage
		attackNumPips = desc.attackNumPips
		attackCooldown = desc.attackCooldown.copy()

		abilities.addAll(desc.abilities.map{ AbstractMonsterAbility.load(it, grid) }.toGdxArray())

		if (difficulty >= 1)
		{
			atkCooldown -= (difficulty.toFloat() / 3f).ciel()

			for (ability in abilities)
			{
				ability.cooldownMin -= (difficulty.toFloat() / 4f).ciel()
				ability.cooldownMax -= (difficulty.toFloat() / 4f).ciel()
			}
		}

		if (difficulty >= 2)
		{
			if (attackNumPips > 4)
			{
				attackNumPips -= (difficulty.toFloat() / 5f).ciel()
				if (attackNumPips < 4)
				{
					attackNumPips = 4
				}
			}
		}

		if (difficulty >= 3)
		{
			attackDamage += (difficulty.toFloat() / 4f).ciel()
		}

		if (desc.attackNumPips > 0)
		{
			var max = desc.attackCooldown.max
			max += (Global.player.getStat(Statistic.HASTE) * max).toInt()

			atkCooldown = (grid.ran.nextFloat() * max).toInt()
		}
		else
		{
			atkCooldown = Int.MAX_VALUE
		}
	}

	override fun onTurn(entity: Entity, grid: Grid)
	{
		if (fastAttacks > 0)
		{
			fastAttacks--

			atkCooldown = 0
		}

		if (powerfulAttacks > 0)
		{
			powerfulAttacks--
		}

		atkCooldown--
		if (atkCooldown <= 0)
		{
			var min = attackCooldown.min
			min += (Global.player.getStat(Statistic.HASTE) * min).toInt()

			var max = attackCooldown.max
			max += (Global.player.getStat(Statistic.HASTE) * max).toInt()

			atkCooldown = min + grid.ran.nextInt(max - min)

			// do attack
			val tile = grid.basicOrbTiles.filter { validAttack(grid, it) }.randomOrNull(grid.ran)

			if (tile?.contents != null)
			{
				val startTile = entity.pos()!!.tile!!

				val damage = if (powerfulAttacks > 0) attackDamage + 2 else attackDamage

				val monsterEffectType = if (damage > 1) MonsterEffectType.BIGATTACK else MonsterEffectType.ATTACK
				val data = MonsterEffectData()
				data.damage = damage

				val monsterEffect = MonsterEffect(monsterEffectType, data)
				monsterEffect.timer = attackNumPips + (Global.player.getStat(Statistic.HASTE) * attackNumPips).toInt()

				addMonsterEffect(tile.contents!!, monsterEffect)

				grid.replay.logAction("Monster ${entity.niceName()} attacking (${tile.toShortString()})")

				if (!Global.resolveInstantly)
				{
					val diff = tile.getPosDiff(startTile)
					diff[0].y *= -1
					entity.renderable()?.renderable?.animation = BumpAnimation.obtain().set(0.2f, diff)

					val dst = tile.euclideanDist(startTile)
					val animDuration = 0.4f + tile.euclideanDist(startTile) * 0.025f
					val attackSprite = monsterEffect.actualSprite.copy()
					attackSprite.colour = tile.contents!!.renderable()!!.renderable.colour
					attackSprite.animation = LeapAnimation.obtain().set(animDuration, diff, 1f + dst * 0.25f)
					attackSprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
					tile.effects.add(attackSprite)

					monsterEffect.delayDisplay = animDuration
				}
			}
		}

		for (ability in abilities)
		{
			ability.cooldownTimer--
			if (ability.cooldownTimer <= 0)
			{
				var min = ability.cooldownMin
				min += (Global.player.getStat(Statistic.HASTE) * min).toInt()

				var max = ability.cooldownMax
				max += (Global.player.getStat(Statistic.HASTE) * max).toInt()

				ability.cooldownTimer = min + grid.ran.nextInt(max - min)
				ability.activate(entity, grid)
			}
		}
	}
}

fun validAttack(grid: Grid, tile: Tile): Boolean
{
	if (tile.spreader != null) return false
	if (tile.contents?.matchable() == null) return false

	// dont allow attacks in choke points
	for (dir in Direction.CardinalValues)
	{
		val ntile = grid.tile(tile + dir) ?: return false
		if (!ntile.canHaveOrb) return false
	}

	return true
}

abstract class AbstractMonsterAbilityData : XmlDataClass()
{
	abstract val classID: String

	var cooldown: Point = Point(1, 1)
	var usages: Int = -1

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		val cooldownRaw = xmlData.get("Cooldown", "1, 1")!!.split(',')
		cooldown = Point(cooldownRaw[0].toInt(), cooldownRaw[1].toInt())
		usages = xmlData.getInt("Usages", -1)
	}

	companion object
	{
		fun loadPolymorphicClass(classID: String): AbstractMonsterAbilityData
		{
		/* Autogenerated method contents. Do not modify. */
			return when (classID)
			{
				"MonsterEffect" -> MonsterMonsterEffectAbilityData()
				"Summon" -> MonsterSummonAbilityData()
				"Spreader" -> MonsterSpreaderAbilityData()
				"Block" -> MonsterBlockAbilityData()
				"Move" -> MonsterMoveAbilityData()
				"SelfBuff" -> MonsterSelfBuffAbilityData()
				"Seal" -> MonsterSealAbilityData()
				else -> throw RuntimeException("Unknown classID '$classID' for AbstractMonsterAbilityData!")
			}
		}
	}
}

abstract class AbstractMonsterAbility<T: AbstractMonsterAbilityData>(val data: T)
{
	var cooldownMin: Int = 1
	var cooldownMax: Int = 1
	var cooldownTimer: Int = 0
	var remainingUsages = -1

	init
	{
		cooldownMin = data.cooldown.x
		cooldownMax = data.cooldown.y
	}

	fun activate(entity: Entity, grid: Grid)
	{
		if (remainingUsages == 0)
		{
			return
		}

		if (remainingUsages != -1)
		{
			remainingUsages--
		}

		if (!Statics.release && !Global.resolveInstantly)
		{
			println("Monster trying to use ability '${this.javaClass.name.split(".Monster")[1].replace("Ability", "")}'")
		}

		doActivate(entity, grid)
	}
	abstract fun doActivate(entity: Entity, grid: Grid)

	companion object
	{
		fun load(data: AbstractMonsterAbilityData, grid: Grid) : AbstractMonsterAbility<*>
		{
			val ability = when (data.classID)
			{
				"MOVE" -> MonsterMoveAbility(data as MonsterMoveAbilityData)
				"MONSTEREFFECT" -> MonsterMonsterEffectAbility(data as MonsterMonsterEffectAbilityData)
				"SEAL" -> MonsterSealAbility(data as MonsterSealAbilityData)
				"SUMMON" -> MonsterSummonAbility(data as MonsterSummonAbilityData)
				"SPREADER" -> MonsterSpreaderAbility(data as MonsterSpreaderAbilityData)
				"SELFBUFF" -> MonsterSelfBuffAbility(data as MonsterSelfBuffAbilityData)
				"BLOCK" -> MonsterBlockAbility(data as MonsterBlockAbilityData)
				else -> throw RuntimeException("Unknown monster ability type '" + data.classID + "'")
			}
			ability.remainingUsages = data.usages
			ability.cooldownTimer = ability.cooldownMin + grid.ran.nextInt(ability.cooldownMax - ability.cooldownMin)

			return ability
		}
	}
}

abstract class TargettedMonsterAbilityData : AbstractMonsterAbilityData()
{
	var range: Point = Point(0, 9999)
	lateinit var targetRestriction: Targetter
	var targetCount: Int = 1
	lateinit var permuter: Permuter

	@DataValue(visibleIf = "Permutter.Type != SINGLE")
	var coverage: Float = 1f

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		val rangeRaw = xmlData.get("Range", "0, 9999")!!.split(',')
		range = Point(rangeRaw[0].toInt(), rangeRaw[1].toInt())
		val targetRestrictionEl = xmlData.getChildByName("TargetRestriction")!!
		targetRestriction = Targetter()
		targetRestriction.load(targetRestrictionEl)
		targetCount = xmlData.getInt("TargetCount", 1)
		val permuterEl = xmlData.getChildByName("Permuter")!!
		permuter = Permuter()
		permuter.load(permuterEl)
		coverage = xmlData.getFloat("Coverage", 1f)
	}

	companion object
	{
		fun loadPolymorphicClass(classID: String): TargettedMonsterAbilityData
		{
		/* Autogenerated method contents. Do not modify. */
			return when (classID)
			{
				"MonsterEffect" -> MonsterMonsterEffectAbilityData()
				"Summon" -> MonsterSummonAbilityData()
				"Spreader" -> MonsterSpreaderAbilityData()
				"Block" -> MonsterBlockAbilityData()
				"Move" -> MonsterMoveAbilityData()
				"Seal" -> MonsterSealAbilityData()
				else -> throw RuntimeException("Unknown classID '$classID' for TargettedMonsterAbilityData!")
			}
		}
	}
}

abstract class TargettedMonsterAbility<T: TargettedMonsterAbilityData>(data: T) : AbstractMonsterAbility<T>(data)
{
	override fun doActivate(entity: Entity, grid: Grid)
	{
		val monsterTile = entity.pos()!!.tile!!

		val availableTargets = Array<Tile>()

		val maxRange = data.range.y
		val minRange = data.range.x

		availableTargets.addAll(grid.basicOrbTiles.filter { it.taxiDist(monsterTile) in minRange..maxRange })

		var validTargets = availableTargets.filter { data.targetRestriction.isValid(it) }

		if (data.targetRestriction.type == TargetterType.ORB)
		{
			validTargets = validTargets.filter { validAttack(grid, it) }
		}

		val chosen = validTargets.asSequence().random(data.targetCount, grid.ran).toList().toGdxArray()

		val finalTargets = Array<Tile>()

		for (target in chosen)
		{
			val source = entity.pos()!!.tile!!
			for (t in data.permuter.permute(target, grid, chosen, null, source))
			{
				if (!finalTargets.contains(t, true))
				{
					finalTargets.add(t)
				}
			}
		}

		if (data.coverage < 1f)
		{
			val chosenCount = (finalTargets.size.toFloat() * data.coverage).ciel()
			while (finalTargets.size > chosenCount)
			{
				finalTargets.removeRandom(grid.ran)
			}
		}

		if (finalTargets.size > 0 && !Global.resolveInstantly)
		{
			val diff = finalTargets[0].getPosDiff(entity.pos()!!.tile!!)
			diff[0].y *= -1
			entity.renderable()?.renderable?.animation = BumpAnimation.obtain().set(0.2f, diff)
		}

		grid.replay.logAction("Activating monster ability $this on targets " + finalTargets.joinToString(" ") { "(${it.toShortString()})" })

		doActivate(entity, grid, finalTargets)
	}

	abstract fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
}

enum class MoveType
{
	BASIC,
	LEAP,
	TELEPORT
}
class MonsterMoveAbilityData : TargettedMonsterAbilityData()
{
	override val classID: String = "Move"

	var isDash: Boolean = false
	lateinit var moveType: MoveType

	var startEffect: ParticleEffect? = null
	var endEffect: ParticleEffect? = null

	@DataValue(visibleIf = "IsDash == True")
	var hitEffect: ParticleEffect? = null

	@DataValue(visibleIf = "IsDash == True")
	var numPips: Int = 8

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		isDash = xmlData.getBoolean("IsDash", false)
		moveType = MoveType.valueOf(xmlData.get("MoveType").toUpperCase(Locale.ENGLISH))
		startEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("StartEffect"))?.getParticleEffect()
		endEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("EndEffect"))?.getParticleEffect()
		hitEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("HitEffect"))?.getParticleEffect()
		numPips = xmlData.getInt("NumPips", 8)
	}
}

class MonsterMoveAbility(data: MonsterMoveAbilityData) : TargettedMonsterAbility<MonsterMoveAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
	{
		val target = targets.filter{ entity.pos()!!.isValidTile(it, entity) }.asSequence().random(grid.ran)

		entity.renderable()?.renderable?.animation = null

		if (target != null)
		{
			val monsterSrc = entity.pos()!!.tile!!

			val dst = monsterSrc.euclideanDist(target)
			var animDuration = 0.25f + dst * 0.025f

			entity.pos()!!.removeFromTile(entity)
			entity.pos()!!.tile = target
			entity.pos()!!.addToTile(entity, animDuration - 0.1f)

			val diff = target.getPosDiff(monsterSrc)
			diff[0].y *= -1

			val moveType: MoveType
			if (data.isDash)
			{
				moveType = MoveType.BASIC
			}
			else
			{
				moveType = data.moveType
			}

			if (moveType == MoveType.LEAP)
			{
				entity.renderable()?.renderable?.animation = LeapAnimation.obtain().set(animDuration, diff, 1f + dst * 0.25f)
				entity.renderable()?.renderable?.animation = ExpandAnimation.obtain().set(animDuration, 1f, 2f, false)
			}
			else if (moveType == MoveType.TELEPORT)
			{
				animDuration = 0.2f
				entity.renderable()?.renderable?.renderDelay = animDuration
				entity.renderable()?.renderable?.showBeforeRender = false
			}
			else
			{
				entity.renderable()?.renderable?.animation = MoveAnimation.obtain().set(animDuration, UnsmoothedPath(diff), Interpolation.linear)
			}

			val startParticle = data.startEffect
			val endParticle = data.endEffect

			if (startParticle != null)
			{
				val particle = startParticle.copy()
				particle.size[0] = entity.pos()!!.size
				particle.size[1] = entity.pos()!!.size

				monsterSrc.effects.add(particle)
			}

			if (endParticle != null)
			{
				val particle = endParticle.copy()
				particle.size[0] = entity.pos()!!.size
				particle.size[1] = entity.pos()!!.size

				particle.renderDelay = animDuration

				target.effects.add(particle)
			}

			if (data.isDash)
			{
				// get line from start to end
				val points = monsterSrc.lineTo(target).toGdxArray()


				val maxDist = monsterSrc.euclideanDist(target)

				val hitEffect = data.hitEffect

				var timer = data.numPips
				timer += (Global.player.getStat(Statistic.HASTE) * timer).toInt()

				// make them all attacks in order
				for (point in points)
				{
					val tile = grid.tile(point)
					if (tile != null && validAttack(grid, tile))
					{
						val dist = monsterSrc.euclideanDist(point)
						val alpha = dist / maxDist
						val delay = animDuration * alpha

						tile.addDelayedAction(
							{
								addMonsterEffect(tile.contents!!, MonsterEffect(MonsterEffectType.ATTACK, MonsterEffectData()))

								if (hitEffect is ParticleEffect)
								{
									val particle = hitEffect.copy()
									tile.effects.add(particle)
								}

							}, delay)
					}
				}
			}
		}
	}
}

enum class EffectType
{
	ATTACK,
	SEALEDATTACK,
	HEAL,
	DELAYEDSUMMON,
	DEBUFF
}
class MonsterMonsterEffectAbilityData : TargettedMonsterAbilityData()
{
	override val classID: String = "MonsterEffect"

	lateinit var effect: EffectType

	var numPips: Int = 8

	@DataValue(visibleIf = "Effect == SEALEDATTACK")
	var sealStrength: Int = 1

	lateinit var monsterEffectData: MonsterEffectData

	var showAttackLeap: Boolean = true
	var flightEffect: ParticleEffect? = null
	var hitEffect: ParticleEffect? = null

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		effect = EffectType.valueOf(xmlData.get("Effect").toUpperCase(Locale.ENGLISH))
		numPips = xmlData.getInt("NumPips", 8)
		sealStrength = xmlData.getInt("SealStrength", 1)
		val monsterEffectDataEl = xmlData.getChildByName("MonsterEffectData")!!
		monsterEffectData = MonsterEffectData()
		monsterEffectData.load(monsterEffectDataEl)
		showAttackLeap = xmlData.getBoolean("ShowAttackLeap", true)
		flightEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("FlightEffect"))?.getParticleEffect()
		hitEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("HitEffect"))?.getParticleEffect()
	}
}

class MonsterMonsterEffectAbility(data: MonsterMonsterEffectAbilityData) : TargettedMonsterAbility<MonsterMonsterEffectAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
	{
		val monsterSrc = entity.pos()!!.tile!!

		for (tile in targets)
		{
			val contents = tile.contents ?: continue
			if (!contents.isBasicOrb()) continue

			var speed = data.numPips
			speed += (Global.player.getStat(Statistic.HASTE) * speed).toInt()

			val monsterEffectType: MonsterEffectType
			if (data.effect == EffectType.HEAL)
			{
				monsterEffectType = MonsterEffectType.HEAL
			}
			else if (data.effect == EffectType.DEBUFF)
			{
				monsterEffectType = MonsterEffectType.DEBUFF
			}
			else if (data.effect == EffectType.DELAYEDSUMMON)
			{
				monsterEffectType = MonsterEffectType.SUMMON
			}
			else
			{
				val dam = data.monsterEffectData.damage
				monsterEffectType = if (dam > 1) MonsterEffectType.BIGATTACK else MonsterEffectType.ATTACK
			}

			val monsterEffect = MonsterEffect(monsterEffectType, data.monsterEffectData)
			addMonsterEffect(contents, monsterEffect)

			monsterEffect.timer = speed
			val diff = tile.getPosDiff(monsterSrc)
			diff[0].y *= -1

			val dst = tile.euclideanDist(monsterSrc)
			var animDuration = dst * 0.025f

			if (data.showAttackLeap)
			{
				animDuration += 0.4f
				val attackSprite = monsterEffect.actualSprite.copy()
				attackSprite.colour = contents.renderable()!!.renderable.colour
				attackSprite.animation = LeapAnimation.obtain().set(animDuration, diff, 1f + dst * 0.25f)
				attackSprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
				tile.effects.add(attackSprite)

				monsterEffect.delayDisplay = animDuration
			}
			else
			{
				val flightEffect = data.flightEffect
				if (flightEffect != null)
				{
					animDuration += 0.4f
					val particle = flightEffect.copy()
					particle.animation = MoveAnimation.obtain().set(animDuration, diff)
					particle.killOnAnimComplete = true

					particle.rotation = getRotation(monsterSrc, tile)

					tile.effects.add(particle)

					monsterEffect.delayDisplay = animDuration
				}
			}

			val hitEffect = data.hitEffect
			if (hitEffect != null)
			{
				val particle = hitEffect.copy()
				particle.renderDelay = animDuration

				animDuration += particle.lifetime / 2f
				monsterEffect.delayDisplay = animDuration

				tile.effects.add(particle)
			}

			if (data.effect == EffectType.SEALEDATTACK)
			{
				val strength = data.sealStrength
				val swappable = tile.contents?.swappable() ?: continue
				swappable.sealCount = strength
			}
		}
	}
}

class MonsterSealAbilityData : TargettedMonsterAbilityData()
{
	override val classID: String = "Seal"

	var sealStrength: Int = 0

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		sealStrength = xmlData.getInt("SealStrength", 0)
	}
}

class MonsterSealAbility(data: MonsterSealAbilityData) : TargettedMonsterAbility<MonsterSealAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
	{
		val strength = data.sealStrength

		for (tile in targets)
		{
			val swappable = tile.contents?.swappable() ?: continue
			swappable.sealCount = strength
		}
	}
}

class MonsterBlockAbilityData : TargettedMonsterAbilityData()
{
	override val classID: String = "Block"

	var blockStrength: Int = 1

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		blockStrength = xmlData.getInt("BlockStrength", 1)
	}
}

class MonsterBlockAbility(data: MonsterBlockAbilityData) : TargettedMonsterAbility<MonsterBlockAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
	{
		val strength = data.blockStrength
		val monsterSrc = entity.pos()!!.tile!!

		for (tile in targets)
		{
			val block = EntityArchetypeCreator.createBlock(grid.level.theme, strength)

			val diff = tile.getPosDiff(monsterSrc)
			diff[0].y *= -1

			val dst = tile.euclideanDist(monsterSrc)
			val animDuration = 0.4f + dst * 0.025f
			val attackSprite = block.renderable()!!.renderable.copy()
			attackSprite.animation = LeapAnimation.obtain().set(animDuration, diff, 1f + dst * 0.25f)
			attackSprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
			tile.effects.add(attackSprite)

			block.renderable()!!.renderable.renderDelay = animDuration
			block.pos()!!.tile = tile
			block.pos()!!.addToTile(block, animDuration)
		}
	}
}

class MonsterSummonAbilityData : TargettedMonsterAbilityData()
{
	override val classID: String = "Summon"

	var monsterDesc: MonsterDesc? = null

	var faction: String? = null
	var name: String? = null
	var difficulty: Int = 0
	var isSummon: Boolean = false

	var spawnEffect: ParticleEffect? = null

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		val monsterDescEl = xmlData.getChildByName("MonsterDesc")
		if (monsterDescEl != null)
		{
			monsterDesc = MonsterDesc()
			monsterDesc!!.load(monsterDescEl)
		}
		faction = xmlData.get("Faction", null)
		name = xmlData.get("Name", null)
		difficulty = xmlData.getInt("Difficulty", 0)
		isSummon = xmlData.getBoolean("IsSummon", false)
		spawnEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("SpawnEffect"))?.getParticleEffect()
	}
}

class MonsterSummonAbility(data: MonsterSummonAbilityData) : TargettedMonsterAbility<MonsterSummonAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
	{
		for (tile in targets)
		{
			var desc = data.monsterDesc
			if (desc == null)
			{
				val factionName = data.faction

				val faction: Faction
				if (!factionName.isNullOrBlank())
				{
					val factionPath = XmlData.enumeratePaths("Factions", "Faction")
						.first { it.toUpperCase(Locale.ENGLISH).endsWith("${factionName.toUpperCase(Locale.ENGLISH)}.XML") }
						.split("Factions/")[1]

					faction = Faction.load(factionPath)
				}
				else
				{
					faction = grid.level.chosenFaction!!
				}

				val name = data.name ?: ""
				desc = if (name.isBlank()) faction.get(1, grid) else (faction.get(name) ?: faction.get(1, grid))
			}

			val difficulty = data.difficulty

			val summoned = desc!!.getEntity(difficulty, data.isSummon, grid)

			summoned.pos()!!.tile = tile
			summoned.pos()!!.addToTile(summoned)

			val spawnEffect = data.spawnEffect
			if (spawnEffect != null)
			{
				tile.effects.add(spawnEffect.copy())
			}
		}
	}
}

enum class SelfBuffType
{
	IMMUNITY,
	FASTATTACKS,
	POWERFULATTACKS
}
class MonsterSelfBuffAbilityData : AbstractMonsterAbilityData()
{
	override val classID: String = "SelfBuff"

	lateinit var buffType: SelfBuffType
	var duration: Int = 1
	var particleEffect: ParticleEffect? = null

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		buffType = SelfBuffType.valueOf(xmlData.get("BuffType").toUpperCase(Locale.ENGLISH))
		duration = xmlData.getInt("Duration", 1)
		particleEffect = AssetManager.tryLoadParticleEffect(xmlData.getChildByName("ParticleEffect"))?.getParticleEffect()
	}
}

class MonsterSelfBuffAbility(data: MonsterSelfBuffAbilityData) : AbstractMonsterAbility<MonsterSelfBuffAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid)
	{
		val duration =  data.duration

		val ai = entity.ai()!!.ai as MonsterAI

		when (data.buffType)
		{
			SelfBuffType.IMMUNITY -> { entity.damageable()!!.immune = true; entity.damageable()!!.immuneCooldown = duration }
			SelfBuffType.FASTATTACKS -> ai.fastAttacks = duration
			SelfBuffType.POWERFULATTACKS -> ai.powerfulAttacks = duration
		}

		val effect = data.particleEffect
		if (effect != null)
		{
			val e = effect.copy()
			e.size[0] = entity.pos()!!.size
			e.size[1] = entity.pos()!!.size
			entity.pos()!!.tile!!.effects.add(e)
		}
	}
}

class MonsterSpreaderAbilityData : TargettedMonsterAbilityData()
{
	override val classID: String = "Spreader"

	lateinit var spreader: SpreaderData

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		val spreaderEl = xmlData.getChildByName("Spreader")!!
		spreader = SpreaderData()
		spreader.load(spreaderEl)
	}
}

class MonsterSpreaderAbility(data: MonsterSpreaderAbilityData) : TargettedMonsterAbility<MonsterSpreaderAbilityData>(data)
{
	override fun doActivate(entity: Entity, grid: Grid, targets: Array<Tile>)
	{
		val monsterSrc = entity.pos()!!.tile!!

		for (tile in targets)
		{
			val diff = tile.getPosDiff(monsterSrc)
			diff[0].y *= -1

			tile.spreader = Spreader(data.spreader)

			tile.spreader!!.spriteWrapper?.chooseSprites()

			val dst = tile.euclideanDist(monsterSrc)
			val animDuration = 0.4f + dst * 0.025f
			val attackSprite = tile.spreader!!.spriteWrapper?.chosenTilingSprite?.copy()
							   ?: tile.spreader!!.spriteWrapper?.chosenSprite?.copy()
							   ?: tile.spreader!!.particleEffect!!.copy()
			attackSprite.animation = LeapAnimation.obtain().set(animDuration, diff, 1f + dst * 0.25f)
			attackSprite.animation = ExpandAnimation.obtain().set(animDuration, 0.5f, 1.5f, false)
			tile.effects.add(attackSprite)

			tile.spreader!!.spriteWrapper?.chosenTilingSprite?.renderDelay = animDuration
			tile.spreader!!.spriteWrapper?.chosenSprite?.renderDelay = animDuration
			tile.spreader!!.particleEffect?.renderDelay = animDuration
		}
	}
}