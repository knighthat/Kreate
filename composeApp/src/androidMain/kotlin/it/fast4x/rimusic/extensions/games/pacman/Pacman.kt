package it.fast4x.rimusic.extensions.games.pacman

import android.util.Log
import android.util.Range
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kreate.compose.R
import it.fast4x.rimusic.extensions.games.pacman.models.DialogState
import it.fast4x.rimusic.extensions.games.pacman.models.EnemyMovementModel
import it.fast4x.rimusic.extensions.games.pacman.models.GameStatsModel
import it.fast4x.rimusic.extensions.games.pacman.models.PacFood
import it.fast4x.rimusic.extensions.games.pacman.ui.Controls
import it.fast4x.rimusic.extensions.games.pacman.ui.GameBorder
import it.fast4x.rimusic.extensions.games.pacman.ui.theme.HeaderFont
import it.fast4x.rimusic.extensions.games.pacman.ui.theme.PacmanBackground
import it.fast4x.rimusic.extensions.games.pacman.ui.theme.PacmanComposeTheme
import it.fast4x.rimusic.extensions.games.pacman.ui.theme.PacmanYellow
import it.fast4x.rimusic.extensions.games.pacman.utils.GameConstants

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Pacman() {
    val gameViewModel = GameViewModel()
    var gameStarted = remember { mutableStateOf(false) }
    var reverseMode = remember { mutableStateOf(false) } // used when bonus food is eaten
    var characterYOffset = remember { mutableStateOf(0f) }
    var characterXOffset = remember { mutableStateOf(0f) }
    var gameStatsModel = GameStatsModel(characterXOffset, characterYOffset, gameStarted , reverseMode)
    var enemyMovementModel = remember { mutableStateOf(EnemyMovementModel()) }
    var gameOverDialogState = remember {
        DialogState(
            shouldShow = mutableStateOf(false),
            mutableStateOf("")
        )
    }
    var foodCounter = remember { mutableStateOf(100) }
    var pacFoodState = remember { PacFood() }


    @ExperimentalFoundationApi
    @Composable
    fun MainScreenContent() {
        val context = LocalContext.current
        Column(
            modifier = Modifier
                .background(color = PacmanBackground)
                .padding(16.dp)
                .fillMaxSize()
        ) {
            Text(
                text = "Pacman",
                fontSize = 36.sp,
                fontFamily = HeaderFont,
                color = PacmanYellow,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(6.dp)
            )
            GameBorder(
                gameViewModel = gameViewModel,
                gameStatsModel = gameStatsModel,
                pacFoodState = pacFoodState,
                resources = context.resources,
                enemyMovementModel = enemyMovementModel.value,
                gameOverDialogState = gameOverDialogState,
                redEnemyDrawable = R.drawable.ghost_red,
                orangeEnemyDrawable = R.drawable.ghost_orange,
                reverseEnemyDrawable = R.drawable.ghost_reverse,

                )
            Controls(
                gameStatsModel = gameStatsModel,
                gameViewModel = gameViewModel,
                upButtonDrawable = R.drawable.arrow_up,
                leftButtonDrawable = R.drawable.arrow_left,
                downButtonDrawable = R.drawable.arrow_down,
                rightButtonDrawable = R.drawable.arrow_right
            )
        }
    }

    fun gameLoop(
        foodCounter: MutableState<Int>,
        pacFoodState: PacFood,
        enemyMovementModel: EnemyMovementModel,
        gameStatsModel: GameStatsModel,
    ) {
        fun resetGame(message: String) {
            gameStarted.value = false
            gameOverDialogState.shouldShow.value = true
            gameOverDialogState.message.value = message
            foodCounter.value = GameConstants.FOOD_COUNTER // reset counter
            pacFoodState.initRedraw()
            // reset character position
            characterXOffset.value = 0f
            characterYOffset.value = 0f

        }

        if (gameStatsModel.isGameStarted.value) {
            // Collision Check
            val characterX = 958.0f / 2 - 90f + gameStatsModel.CharacterXOffset.value
            val characterY = 1290.0f - 155f + gameStatsModel.CharacterYOffset.value

            // normal food collision
            pacFoodState.foodList.forEach { foodModel ->
                if (
                    Range.create(characterX, characterX + 100).contains(foodModel.xPos.toFloat()) &&
                    Range.create(characterY, characterY + 100).contains(foodModel.yPos.toFloat())
                ) {
                    // redraw outside box with 0 size and increment score by 1
                    foodModel.xPos = 1000
                    foodModel.yPos = 2000
                    foodCounter.value -= 1
                }
            }

            // bonus food collision
            pacFoodState.bonusFoodList.forEach { foodModel ->
                if (
                    Range.create(characterX, characterX + 100).contains(foodModel.xPos.toFloat()) &&
                    Range.create(characterY, characterY + 100).contains(foodModel.yPos.toFloat())
                ) {
                    // redraw outside box with 0 size
                    reverseMode.value = true
                    foodModel.xPos = 1000
                    foodModel.yPos = 2000
                }
            }

            // reverse mode detection

            if(enemyMovementModel.orangeEnemyMovement.value.x == 409.0f &&
                enemyMovementModel.orangeEnemyMovement.value.y == 705.0f &&
                enemyMovementModel.redEnemyMovement.value.x == 389.0f &&
                enemyMovementModel.redEnemyMovement.value.y == 705.0f
            ){
                /*
                 if these conditions are true the game is either started or the reverse animation has finished
                 so reverseMode should be set to false.
                 */
                gameStatsModel.isReverseMode.value = false
            }

            // enemy collision detection
            Log.d(
                "enemyMovement", "" +
                        "Orange : x: ${enemyMovementModel.orangeEnemyMovement.value.x} " +
                        "y: ${enemyMovementModel.orangeEnemyMovement.value.y} Red: x:" +
                        " ${enemyMovementModel.redEnemyMovement.value.x} y: " +
                        "${enemyMovementModel.redEnemyMovement.value.y} character current position : x:" +
                        " $characterX y : $characterY"
            )

            if (
            // if enemy is within 100f of character then a collision has occurred and the game should stop
                Range.create(characterX, characterX + 25).contains(
                    enemyMovementModel.redEnemyMovement.value.x
                ) &&
                Range.create(characterY, characterY + 25).contains(
                    enemyMovementModel.redEnemyMovement.value.y
                ) ||
                Range.create(characterX, characterX + 25).contains(
                    enemyMovementModel.orangeEnemyMovement.value.x
                ) &&
                Range.create(characterY, characterY + 25).contains(
                    enemyMovementModel.orangeEnemyMovement.value.y
                )

            ) {
                // gameOver, stop game and show dialog
                resetGame("GAME OVER")

            }

            // win logic
            //Log.d("food counter", "counter: ${foodCounter.value} ")
            if (foodCounter.value == 0) {
                resetGame("YOU WON !")
            }


        }
    }

    PacmanComposeTheme {
        // A surface container using the 'background' color from the theme
        Surface(color = MaterialTheme.colorScheme.background) {
            MainScreenContent()
        }
    }

    gameLoop(
        gameStatsModel = gameStatsModel,
        pacFoodState = pacFoodState,
        enemyMovementModel = enemyMovementModel.value,
        foodCounter = foodCounter
    )


}

