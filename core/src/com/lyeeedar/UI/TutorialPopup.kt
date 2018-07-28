package com.lyeeedar.UI

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.Actions.*
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Table
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.lyeeedar.Global
import com.lyeeedar.Util.AssetManager
import ktx.actors.alpha
import ktx.actors.onClick
import ktx.actors.then

class TutorialPopup(val text: String, val emphasis: Rectangle, val advance: () -> Unit) : Table()
{
	fun show()
	{
		val animSpeed = 0.3f

		val greyoutalpha = 0.9f

		// add greyout
		val topGreyout = Table()
		topGreyout.alpha = 0f
		topGreyout.touchable = Touchable.enabled
		topGreyout.background = TextureRegionDrawable(AssetManager.loadTextureRegion ("white")).tint(Color(0f, 0f, 0f, greyoutalpha))
		topGreyout.setBounds(0f, 0f, Global.stage.width, emphasis.y)
		topGreyout.addAction(alpha(0f) then fadeIn(animSpeed))
		Global.stage.addActor(topGreyout)

		val bottomGreyout = Table()
		bottomGreyout.alpha = 0f
		bottomGreyout.touchable = Touchable.enabled
		bottomGreyout.background = TextureRegionDrawable(AssetManager.loadTextureRegion ("white")).tint(Color(0f, 0f, 0f, greyoutalpha))
		bottomGreyout.setBounds(0f, emphasis.y + emphasis.height, Global.stage.width, Global.stage.height - (emphasis.y + emphasis.height))
		bottomGreyout.addAction(alpha(0f) then fadeIn(animSpeed))
		Global.stage.addActor(bottomGreyout)

		val leftGreyout = Table()
		leftGreyout.alpha = 0f
		leftGreyout.touchable = Touchable.enabled
		leftGreyout.background = TextureRegionDrawable(AssetManager.loadTextureRegion ("white")).tint(Color(0f, 0f, 0f, greyoutalpha))
		leftGreyout.setBounds(0f, emphasis.y, emphasis.x, emphasis.height)
		leftGreyout.addAction(alpha(0f) then fadeIn(animSpeed))
		Global.stage.addActor(leftGreyout)

		val rightGreyout = Table()
		rightGreyout.alpha = 0f
		rightGreyout.touchable = Touchable.enabled
		rightGreyout.background = TextureRegionDrawable(AssetManager.loadTextureRegion ("white")).tint(Color(0f, 0f, 0f, greyoutalpha))
		rightGreyout.setBounds(emphasis.x + emphasis.width, emphasis.y, Global.stage.width - (emphasis.x + emphasis.width), emphasis.height)
		rightGreyout.addAction(alpha(0f) then fadeIn(animSpeed))
		Global.stage.addActor(rightGreyout)

		val centerBlock = Table()
		centerBlock.alpha = 0f
		centerBlock.touchable = Touchable.enabled
		centerBlock.background = NinePatchDrawable(NinePatch(AssetManager.loadTextureRegion("GUI/border"), 8, 8, 8, 8)).tint(Color.GOLD)
		centerBlock.setBounds(emphasis.x, emphasis.y, emphasis.width, emphasis.height)
		if (emphasis.width != 0f && emphasis.height != 0f) centerBlock.addAction(alpha(0f) then fadeIn(animSpeed))
		Global.stage.addActor(centerBlock)

		// add popup
		background = NinePatchDrawable(NinePatch(AssetManager.loadTextureRegion("Sprites/GUI/background.png"), 24, 24, 24, 24)).tint(Color(1f, 1f, 1f, 0.7f))
		touchable = Touchable.enabled

		val label = Label(text, Global.skin)
		label.setWrap(true)

		this.alpha = 0f
		add(label).grow().width(Global.stage.width * 0.75f)

		addAction(alpha(0f) then fadeIn(animSpeed))

		onClick { inputEvent, tutorialPopup ->
			advance.invoke()
			addAction(fadeOut(0.1f) then removeActor())
			topGreyout.addAction(fadeOut(0.1f) then removeActor())
			bottomGreyout.addAction(fadeOut(0.1f) then removeActor())
			leftGreyout.addAction(fadeOut(0.1f) then removeActor())
			rightGreyout.addAction(fadeOut(0.1f) then removeActor())
			centerBlock.addAction(fadeOut(0.1f) then removeActor())
		}

		pack()

		val placeTop = emphasis.y > Global.stage.height / 2f

		val px = (emphasis.x + emphasis.width / 2f) - width / 2f
		var py = 0f
		if (placeTop)
		{
			py = emphasis.y - height - 20
		}
		else
		{
			py = emphasis.y + emphasis.height + 20
		}

		setPosition(px, py)
		ensureOnScreen(5f)
		Global.stage.addActor(this)
	}

	constructor(text: String, emphasis: Actor, advance: () -> Unit): this(text, emphasis.getBounds(), advance)
}