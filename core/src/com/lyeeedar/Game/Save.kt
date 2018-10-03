package com.lyeeedar.Game

import com.badlogic.gdx.Gdx
import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.lyeeedar.*
import com.lyeeedar.Card.Card
import com.lyeeedar.Card.CardContent.CardContent
import com.lyeeedar.Screens.CardScreen
import com.lyeeedar.Screens.DeckScreen
import com.lyeeedar.Screens.QuestScreen
import com.lyeeedar.Screens.QuestSelectionScreen
import com.lyeeedar.Util.registerGdxSerialisers
import com.lyeeedar.Util.registerLyeeedarSerialisers
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class Save
{
	companion object
	{
		val kryo: Kryo by lazy { initKryo() }
		fun initKryo(): Kryo
		{
			val kryo = Kryo()
			kryo.isRegistrationRequired = false

			kryo.registerGdxSerialisers()
			kryo.registerLyeeedarSerialisers()

			return kryo
		}

		fun save()
		{
			if (doingLoad) return

			val outputFile = Gdx.files.local("save.dat")

			val output: Output
			try
			{
				output = Output(GZIPOutputStream(outputFile.write(false)))
			}
			catch (e: Exception)
			{
				e.printStackTrace()
				return
			}

			// Obtain all data

			// Save all data
			Global.deck.save(output)
			Global.player.save(kryo, output)
			Global.globalflags.save(kryo, output)
			Global.questflags.save(kryo, output)
			Global.cardflags.save(kryo, output)
			Global.settings.save(kryo, output)

			val currentScreen = Global.game.currentScreenEnum
			output.writeInt(currentScreen.ordinal)

			if (currentScreen == MainGame.ScreenEnum.QUESTSELECTION)
			{

			}
			else if (currentScreen == MainGame.ScreenEnum.DECK)
			{

			}
			else
			{
				val questScreen = Global.game.getTypedScreen<QuestScreen>()!!
				questScreen.currentQuest.save(output)

				if (currentScreen == MainGame.ScreenEnum.QUEST)
				{

				}
				else
				{
					val cardScreen = Global.game.getTypedScreen<CardScreen>()!!
					output.writeString(cardScreen.currentCard.path)

					cardScreen.currentContent.save(kryo, output)
				}
			}

			output.close()
		}

		var doingLoad = false
		fun load(): Boolean
		{
			doingLoad = true
			var input: Input? = null

			try
			{
				val saveFileHandle = Gdx.files.local("save.dat")
				if (!saveFileHandle.exists())
				{
					doingLoad = false
					return false
				}

				input = Input(GZIPInputStream(saveFileHandle.read()))

				// Load all data
				val deck = GlobalDeck.load(input)
				val player = Player.load(kryo, input, deck)
				val globalFlags = GameStateFlags.load(kryo, input)
				val questFlags = GameStateFlags.load(kryo, input)
				val cardFlags = GameStateFlags.load(kryo, input)
				val settings = Settings.load(kryo, input)

				val currentScreen = MainGame.ScreenEnum.values()[input.readInt()]

				// If successful set data to active objects
				Global.deck = deck
				Global.player = player
				Global.globalflags = globalFlags
				Global.questflags = questFlags
				Global.cardflags = cardFlags
				Global.settings = settings

				if (currentScreen == MainGame.ScreenEnum.QUESTSELECTION)
				{
					val screen = Global.game.getTypedScreen<QuestSelectionScreen>()!!
					screen.setup()
					screen.swapTo()
				}
				else if (currentScreen == MainGame.ScreenEnum.DECK)
				{
					val screen = Global.game.getTypedScreen<DeckScreen>()!!
					screen.setup()
					screen.swapTo()
				}
				else
				{
					val currentQuest = Quest.load(input)
					Global.deck.quests.replace(currentQuest)

					val questScreen = Global.game.getTypedScreen<QuestScreen>()!!
					questScreen.setup(currentQuest)

					if (currentScreen == MainGame.ScreenEnum.QUEST)
					{

					}
					else
					{
						val currentCardPath = input.readString()
						val currentCard = Card.Companion.load(currentCardPath)
						Global.deck.encounters.replace(currentCard)

						val content = CardContent.load(kryo, input)

						val cardScreen = Global.game.getTypedScreen<CardScreen>()!!
						cardScreen.setup(currentCard, currentQuest, false, content)
					}

					// try swapping to the desired screen
					val screens = arrayOf(MainGame.ScreenEnum.GRID, MainGame.ScreenEnum.CARD, MainGame.ScreenEnum.QUEST, MainGame.ScreenEnum.QUESTSELECTION)
					val current = screens.indexOf(currentScreen)

					for (i in current until screens.size)
					{
						try
						{
							Global.game.switchScreen(screens[i])
							break
						}
						catch (ex: Exception)
						{
							if (!Global.release)
							{
								throw ex
							}
						}
					}
				}
			}
			catch (ex: Exception)
			{
				if (!Global.release)
				{
					throw ex
				}

				doingLoad = false
				return false
			}
			finally
			{
				input?.close()
			}

			doingLoad = false
			return true
		}
	}
}