package it.fast4x.rimusic.extensions.games.snake

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kreate.compose.R
import kotlinx.coroutines.delay
import kotlin.random.Random

data class Cell(val x: Int, val y: Int)

enum class Direction {
    UP, DOWN, LEFT, RIGHT
}

@Composable
fun SnakeGame() {
    val gridSize = 24 // Size of the game grid
    // Game state variables
    var direction by remember { mutableStateOf(Direction.RIGHT) } // Initial direction of the snake
    var snake by remember { mutableStateOf(listOf(Cell(5, 5))) } // Initial position of the snake
    var food by remember {
        mutableStateOf(
            generateFood(
                snake,
                gridSize
            )
        )
    } // Initial position of the food
    var isGameOver by remember { mutableStateOf(false) } // Game over state
    var gameId by remember { mutableStateOf(0) } // Game id used to restart the game
    var gameSpeed by remember { mutableStateOf(50L) } // Game speed in milliseconds
    var snakeSpeed by remember { mutableStateOf(200L) } // Snake movement speed in milliseconds
    var lastMoveTime by remember { mutableStateOf(0L) } // Time when the snake last moved

    // Game loop, the key is gameId so the loop restarts when the game restarts
    // Game loop
    LaunchedEffect(gameId) {
        while (!isGameOver) {
            delay(gameSpeed) // Delay between game frames
            // Only move the snake if enough time has passed since the last move
            if (System.currentTimeMillis() - lastMoveTime >= snakeSpeed) {
                snake = moveSnake(snake, direction) // Move the snake in the current direction
                lastMoveTime = System.currentTimeMillis() // Update the last move time
            }
            if (snake.first() == food) {
                // Snake ate the food, generate new food and grow the snake
                food = generateFood(snake, gridSize)
                snake = growSnake(snake, direction, gridSize)
            }
            isGameOver = checkGameOver(snake, gridSize) // Check if the game is over
        }
    }

    // Game screen
    Box(modifier = Modifier.fillMaxSize()) {
        if (isGameOver) {
            // Game over screen
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = stringResource(R.string.game_over), color = Color.Red)
                Button(onClick = {
                    // Restart the game
                    snake = listOf(Cell(5, 5))
                    direction = Direction.RIGHT
                    food = generateFood(snake, gridSize)
                    isGameOver = false
                    gameId++ // Increment gameId to trigger a game restart
                }) {
                    Text(stringResource(R.string.game_restart))
                }
            }
        } else {
            BasicText(text = "Snake Game")
            // Game board
            GameBoard(snake, food, gridSize, direction, { direction = it })
        }
    }
}

// Game board and controls
@Composable
fun GameBoard(
    snake: List<Cell>,
    food: Cell,
    gridSize: Int,
    currentDirection: Direction,
    onDirectionChange: (Direction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Grid(snake, food, gridSize)
        Spacer(modifier = Modifier.height(16.dp))
        Controls(currentDirection, onDirectionChange)
    }
}

// The game grid
@Composable
fun Grid(snake: List<Cell>, food: Cell, gridSize: Int) {
    val cellSize = 16.dp

    Column(modifier = Modifier.background(color = Color.Red)) {
        for (i in 0 until gridSize) {
            Row {
                for (j in 0 until gridSize) {
                    Box(
                        modifier = Modifier
                            .size(cellSize)
                            .border(border = BorderStroke(0.2.dp, Color.LightGray))
                            .background(
                                when (Cell(j, i)) {
                                    in snake -> Color.Green // Snake cell
                                    food -> Color.Red // Food cell
                                    else -> Color.White // Empty cell
                                }
                            )
                    )
                }
            }
        }
    }
}

// Controls for the game
@Composable
fun Controls(currentDirection: Direction, onDirectionChange: (Direction) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            // Up button
            ControlButton(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up"
            ) {
                if (currentDirection != Direction.DOWN) onDirectionChange(Direction.UP)
            }
        }
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(8.dp)
        ) {
            // Left button
            ControlButton(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Left"
            ) {
                if (currentDirection != Direction.RIGHT) onDirectionChange(Direction.LEFT)
            }

            Spacer(modifier = Modifier.width(40.dp))

            // Right button
            ControlButton(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Right"
            ) {
                if (currentDirection != Direction.LEFT) onDirectionChange(Direction.RIGHT)
            }
        }
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            // Down button
            ControlButton(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down"
            ) {
                if (currentDirection != Direction.UP) onDirectionChange(Direction.DOWN)
            }

        }
    }
}

@Composable
fun ControlButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        modifier = Modifier
            .clip(
                RoundedCornerShape(20.dp)
            )
            .background(color = Color.LightGray),
        onClick = onClick
    ) {
        Icon(modifier = Modifier
            .size(80.dp), imageVector = imageVector, contentDescription = contentDescription)
    }
}

// Moves the snake in the given direction and returns the new snake
fun moveSnake(snake: List<Cell>, direction: Direction): List<Cell> {
    val head = snake.first()
    val newHead = when (direction) {
        Direction.UP -> Cell(head.x, head.y - 1)
        Direction.DOWN -> Cell(head.x, head.y + 1)
        Direction.LEFT -> Cell(head.x - 1, head.y)
        Direction.RIGHT -> Cell(head.x + 1, head.y)
    }
    val newSnake = snake.toMutableList()
    newSnake.add(0, newHead)
    newSnake.removeAt(newSnake.size - 1)
    return newSnake
}

// Generates a new food cell not occupied by the snake
fun generateFood(snake: List<Cell>, gridSize: Int): Cell {
    val emptyCells = (0 until gridSize).flatMap { x ->
        (0 until gridSize).map { y -> Cell(x, y) }
    }.filter { it !in snake }
    return emptyCells[Random.nextInt(emptyCells.size)]
}

// Grows the snake in the given direction and returns the new snake
fun growSnake(snake: List<Cell>, direction: Direction, gridSize: Int): List<Cell> {
    val growth = when (direction) {
        Direction.UP -> Cell(snake.first().x, (snake.first().y - 1 + gridSize) % gridSize)
        Direction.DOWN -> Cell(snake.first().x, (snake.first().y + 1) % gridSize)
        Direction.LEFT -> Cell((snake.first().x - 1 + gridSize) % gridSize, snake.first().y)
        Direction.RIGHT -> Cell((snake.first().x + 1) % gridSize, snake.first().y)
    }
    return listOf(growth) + snake
}

// Checks if the game is over
fun checkGameOver(snake: List<Cell>, gridSize: Int): Boolean {
    val head = snake.first()
    return head in snake.drop(1) || head.x < 0 || head.y < 0 || head.x >= gridSize || head.y >= gridSize
}