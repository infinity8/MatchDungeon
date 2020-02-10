package com.lyeeedar.Board.CompletionCondition

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.utils.Align
import com.lyeeedar.Board.Grid
import com.lyeeedar.Game.Global
import com.lyeeedar.Renderables.Animation.ExpandAnimation
import com.lyeeedar.Statistic
import com.lyeeedar.UI.SpriteWidget
import com.lyeeedar.UI.Tutorial
import com.lyeeedar.Util.*

@DataClass(name = "Time")
class CompletionConditionTimeData : AbstractCompletionConditionData()
{
	override val classID: String = "Time"
	
	@DataValue(dataName = "Seconds")
	var time: Float = 60f

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		super.load(xmlData)
		time = xmlData.getFloat("Seconds", 60f)
	}
}

class CompletionConditionTime(data: CompletionConditionTimeData): AbstractCompletionCondition<CompletionConditionTimeData>(data)
{
	var remainingTime: Float = 60f
	var maxTime = data.time

	lateinit var label: Label
	val blinkTable = Table()

	override fun createTable(grid: Grid): Table
	{
		val t = remainingTime.toInt()
		label = Label("$t\n" + Localisation.getText("completioncondition.time.seconds", "UI"), Statics.skin)
		label.setAlignment(Align.center)

		val stack = Stack()
		stack.add(blinkTable)
		stack.add(label)

		val table = Table()
		table.defaults().pad(10f)
		table.add(stack)

		return table
	}

	override fun attachHandlers(grid: Grid)
	{
		maxTime += (Global.player.getStat(Statistic.HASTE) * maxTime).toInt()
		remainingTime = maxTime

		grid.onTime +=
				{
					if (!Global.godMode)
					{
						remainingTime -= it
					}

					val t = remainingTime.toInt()
					label.setText("$t\n" + Localisation.getText("completioncondition.time.seconds", "UI"))

					if (remainingTime <= maxTime * 0.25f && blinkTable.children.size == 0)
					{
						val blinkSprite = AssetManager.loadSprite("Particle/glow")
						blinkSprite.colour = Colour.RED.copy().a(0.5f)
						blinkSprite.animation = ExpandAnimation.obtain().set(1f, 0.5f, 2f, false, true)
						val actor = SpriteWidget(blinkSprite, 32f, 32f)
						blinkTable.add(actor).grow()
					}

					HandlerAction.KeepAttached
				}

		if (!Global.resolveInstantly)
		{
			Future.call(
				{
					val tutorial = Tutorial("Time")
					tutorial.addPopup(Localisation.getText("completioncondition.time.tutorial", "UI"), label)
					tutorial.show()
				}, 0.5f)
		}
	}

	override fun isCompleted(): Boolean = remainingTime <= 0

	override fun getDescription(grid: Grid): Table
	{
		val table = Table()

		val t = remainingTime.toInt()
		var text = Localisation.getText("completioncondition.time.description", "UI")
		text = text.replace("{Time}", t.toString())

		table.add(Label(text, Statics.skin))

		return table
	}
}