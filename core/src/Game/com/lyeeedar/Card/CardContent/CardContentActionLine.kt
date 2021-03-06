package com.lyeeedar.Card.CardContent

import com.badlogic.gdx.utils.ObjectMap
import com.lyeeedar.Screens.CardScreen
import com.lyeeedar.Util.Localisation
import com.lyeeedar.Util.XmlData
import com.lyeeedar.Util.expandVariables

class CardContentActionLine : AbstractCardContentAction()
{
	lateinit var textID: String

	override fun parse(xmlData: XmlData)
	{
		textID = xmlData.get("MultilineString")
	}

	override fun advance(CardContent: CardContent, CardContentScreen: CardScreen): Boolean
	{
		val expanded = Localisation.getText(textID, "CardContent").expandVariables()

		val scrollingTextLabel = CardContentScreen.text
		if (scrollingTextLabel.text.toString() == expanded)
		{
			if (scrollingTextLabel.isComplete)
			{
				return true
			}
			else
			{
				scrollingTextLabel.isComplete = true
				return false
			}
		}

		if (scrollingTextLabel.text.toString() != expanded)
		{
			scrollingTextLabel.setText(expanded)
		}

		return false
	}

	override fun resolve(nodeMap: ObjectMap<String, CardContentNode>)
	{

	}
}