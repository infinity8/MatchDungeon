package com.lyeeedar.Game

import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Stack
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.lyeeedar.Renderables.Sprite.Sprite
import com.lyeeedar.Statistic
import com.lyeeedar.UI.CardWidget
import com.lyeeedar.UI.FrontTableSimple
import com.lyeeedar.UI.Seperator
import com.lyeeedar.UI.SpriteWidget
import com.lyeeedar.Util.*
import com.lyeeedar.Util.AssetManager

@DataClass(name = "Buff")
class BuffData : XmlDataClass()
{
	@DataValue(dataName = "Name")
	@NeedsLocalisation(file = "Buff")
	lateinit var nameID: String
	lateinit var icon: Sprite
	val statistics: FastEnumMap<Statistic, Float> = FastEnumMap<Statistic, Float>(Statistic::class.java)
	
	@NumericRange(min = 1f)
	var duration: Int = 4

	override fun load(xmlData: XmlData)
	{
	/* Autogenerated method contents. Do not modify. */
		nameID = xmlData.get("Name")
		icon = AssetManager.loadSprite(xmlData.getChildByName("Icon")!!)
		Statistic.parse(xmlData.getChildByName("Statistics")!!, statistics)
		duration = xmlData.getInt("Duration", 4)
	}
}

class Buff(val data: BuffData)
{
	var remainingDuration: Int = data.duration

	fun getCard(): CardWidget
	{
		return CardWidget.createCard(Localisation.getText(data.nameID, "Buff"), Localisation.getText("buff", "UI"), data.icon.copy(), createTable(), AssetManager.loadTextureRegion("GUI/BuffCardback")!!, this)
	}

	fun getCardSmall(isBuff: Boolean): CardWidget
	{
		val icon = if (isBuff)
			AssetManager.loadSprite("GUI/Buff")
		else
			AssetManager.loadSprite("GUI/Debuff")

		val basicTable = CardWidget.createFrontTable(FrontTableSimple(Localisation.getText(data.nameID, "Buff"), Localisation.getText("buff", "UI"), icon))

		val card = CardWidget(basicTable, createTable(), icon.currentTexture, this)
		return card
	}

	fun createTable(showTurns: Boolean = true): Table
	{
		val table = Table()
		table.defaults().growX()

		val titleStack = Stack()
		val iconTable = Table()
		iconTable.add(SpriteWidget(data.icon, 64f, 64f)).expandX().right().pad(5f)
		titleStack.add(iconTable)
		titleStack.add(Label(Localisation.getText(data.nameID, "Buff"), Statics.skin, "cardtitle"))

		table.add(titleStack).growX()
		table.row()

		table.add(Seperator(Statics.skin, "horizontalcard")).pad(10f, 0f, 10f, 0f)
		table.row()

		if (showTurns)
		{
			table.add(Label(Localisation.getText("buff.remainingduration", "UI") + ": $remainingDuration", Statics.skin, "card")).pad(5f)
			table.row()
		}

		if (data.statistics.any { it != 0f })
		{
			table.add(Seperator(Statics.skin, "horizontalcard")).pad(10f, 0f, 10f, 0f)
			table.row()

			table.add(Label(Localisation.getText("statistics", "UI"), Statics.skin, "cardtitle"))
			table.row()

			table.add(Statistic.createTable(data.statistics, Statistic.Companion.DisplayType.MODIFIER)).growX()
			table.row()
		}

		return table
	}

	fun copy(): Buff
	{
		return Buff(data)
	}

	fun save(kryo: Kryo, output: Output)
	{
		kryo.writeObject(output, data)
		output.writeInt(remainingDuration, true)
	}

	companion object
	{
		fun load(xml: XmlData): Buff
		{
			val data = BuffData()
			data.load(xml)

			val buff = Buff(data)
			return buff
		}

		fun load(kryo: Kryo, input: Input): Buff
		{
			val xml = kryo.readObject(input, XmlData::class.java)
			val buff = load(xml)
			buff.remainingDuration = input.readInt(true)
			return buff
		}
	}
}