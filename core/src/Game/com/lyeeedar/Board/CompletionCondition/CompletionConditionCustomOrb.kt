package com.lyeeedar.Board.CompletionCondition

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.lyeeedar.Board.Grid
import com.lyeeedar.Board.OrbDesc
import com.lyeeedar.Components.Entity
import com.lyeeedar.Components.matchable
import com.lyeeedar.Game.Global
import com.lyeeedar.UI.SpriteWidget
import com.lyeeedar.UI.Tutorial
import com.lyeeedar.Util.*

@DataClass(name = "CustomOrb")
class CompletionConditionCustomOrbData : AbstractCompletionConditionData()
{
	override val classID: String = "CustomOrb"
	
	lateinit var targetOrbName: String
	var matchCount: Int = 0
	var orbChance: Float = 0f

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		targetOrbName = xmlData.get("TargetOrbName")
		matchCount = xmlData.getInt("MatchCount", 0)
		orbChance = xmlData.getFloat("OrbChance", 0f)
	}
}

class CompletionConditionCustomOrb(data: CompletionConditionCustomOrbData) : AbstractCompletionCondition<CompletionConditionCustomOrbData>(data)
{
	val tick = AssetManager.loadSprite("Oryx/uf_split/uf_interface/uf_interface_680", colour = Colour(Color.FOREST))
	var remainingCount = data.matchCount

	val table = Table()

	override fun attachHandlers(grid: Grid)
	{
		grid.onPop += fun(orb: Entity, delay: Float) : HandlerAction {

			if (orb.matchable()?.desc?.name == data.targetOrbName)
			{
				if (remainingCount > 0) remainingCount--
				rebuildWidget()
			}

			return HandlerAction.KeepAttached
		}

		if (!Global.resolveInstantly)
		{
			Future.call(
				{
					val tutorial = Tutorial("CustomOrbComplete")
					tutorial.addPopup(Localisation.getText("completioncondition.customorb.tutorial", "UI"), table)
					tutorial.show()
				}, 0.5f)
		}
	}

	override fun isCompleted(): Boolean
	{
		return remainingCount == 0
	}

	override fun createTable(grid: Grid): Table
	{
		rebuildWidget()

		return table
	}

	fun rebuildWidget()
	{
		table.clear()

		val targetDesc = OrbDesc.getNamedOrb(data.targetOrbName)
		val sprite = targetDesc.sprite

		table.add(SpriteWidget(sprite, 24f, 24f)).padLeft(5f)

		if (remainingCount == 0)
		{
			table.add(SpriteWidget(tick, 24f, 24f))
		}
		else
		{
			table.add(Label(" x $remainingCount", Statics.skin))
		}
	}

	override fun getDescription(grid: Grid): Table
	{
		val table = Table()

		table.add(Label(Localisation.getText("completioncondition.customorb.description", "UI"), Statics.skin))

		val targetDesc = OrbDesc.getNamedOrb(data.targetOrbName)
		val sprite = targetDesc.sprite

		table.add(SpriteWidget(sprite, 24f, 24f)).padLeft(5f)
		table.add(Label(" x ${data.matchCount}", Statics.skin))

		return table
	}
}