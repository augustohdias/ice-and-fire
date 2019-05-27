import java.io.File
import java.util.*
import kotlin.math.abs

data class Mine(val x: Int, val y: Int, var ownership: Int)

data class Cell(var x: Int, var y: Int, var ownership: Int, var piece: Piece? = null) {
    fun distance(other: Cell) = abs(x - other.x) + abs(y - other.y)
    fun distance(xx: Int, yy: Int) = abs(x - xx) + abs(y - yy)
}

data class Piece(val id: Int, val isFriendly: Boolean, val level: Int, var type: Int = 0)

data class Board(val board: MutableList<MutableList<Cell?>>) {
    companion object {
        val TOWER = 2
        val MINE = 1
        val EMPTY = 0
        val UNIT = -1
    }

    var lastId: Int = 0

    var towers: MutableList<Cell> = mutableListOf()
    lateinit var myHQ: Cell
    lateinit var enemyHQ: Cell

    fun addTower(cell: Cell) {
        towers.plusAssign(cell)
    }

    fun getCell(x: Int, y: Int): Cell? {
        return board[y][x]
    }

    fun getFlattenBoard(): List<Cell?> {
        return board.flatten()
    }

    fun lookAround(x: Int, y: Int): MutableList<Cell> {
        return when {
            x == 0 && y == 0 -> mutableListOf(board[y + 1][x], board[y][x + 1])
            x == 0 && y == 11 -> mutableListOf(board[y - 1][x], board[y][x + 1])
            x == 11 && y == 0 -> mutableListOf(board[y + 1][x], board[y][x - 1])
            x == 11 && y == 11 -> mutableListOf(board[y - 1][x], board[y][x - 1])
            x == 0 -> mutableListOf(board[y + 1][x], board[y - 1][x], board[y][x + 1])
            x == 11 -> mutableListOf(board[y + 1][x], board[y - 1][x], board[y][x - 1])
            y == 0 -> mutableListOf(board[y + 1][x], board[y][x + 1], board[y][x - 1])
            y == 11 -> mutableListOf(board[y - 1][x], board[y][x + 1], board[y][x - 1])
            else -> mutableListOf(board[y + 1][x], board[y - 1][x], board[y][x + 1], board[y][x - 1])
        }
            .filterNotNull()
            .toMutableList()
    }

    fun lookAround(cell: Cell): MutableList<Cell> {
        return lookAround(cell.x, cell.y)
            .filterNotNull()
            .toMutableList()
    }

    fun updateCell(x: Int, y: Int, newCell: Cell) {
        board[y][x] = newCell
    }

    fun moveTo(cell: Cell, x: Int, y: Int): Boolean {
        if (cell.distance(x, y) == 1) {
            board[y][x]?.piece = cell.piece
            cell.piece = null
            return true
        }
        return false
    }

    fun myCells() = getFlattenBoard().filterNotNull().filter { it.ownership == 2 }

    fun lowerPieces() = myCells().filter { it.piece?.level == 1 }

    fun midlePieces() = myCells().filter { it.piece?.level == 2 }

    fun higherPieces() = myCells().filter { it.piece?.level == 3 }

    fun myPieces() = lowerPieces() + midlePieces() + higherPieces()

    fun parseUnits(input: Scanner) {
        repeat(input.nextInt()) {
            val owner = input.nextInt()
            val unitId = input.nextInt()
            val level = input.nextInt()
            val x = input.nextInt()
            val y = input.nextInt()
            val cell = getCell(x, y)!!
            cell.piece = Piece(unitId, owner == 0, level, Board.UNIT)
            if (unitId > lastId) {
                lastId = unitId
            }
        }
    }

    fun parseBuildings(input: Scanner) {
        val buildingCount = input.nextInt()
        for (i in 0 until buildingCount) {
            val owner = input.nextInt()
            val buildingType = input.nextInt()
            val x = input.nextInt()
            val y = input.nextInt()
            if (buildingType == 0) {
                val hq = getCell(x, y)!!
                when (owner) {
                    0 -> myHQ = hq
                    1 -> enemyHQ = hq
                }
            } else if (buildingType == 2) {
                getCell(x, y)!!.piece = Piece(Int.MAX_VALUE, owner == 0, 3, Board.TOWER)
            }
        }
    }

    fun evaluatePath(winPath: List<Cell>): Int {
        var expentGold = 0
        winPath.forEach { cell ->
            expentGold += when {
                cell.piece == null && lookAround(cell).any { it.piece?.type == Board.TOWER } -> 30
                cell.piece == null -> 10
                cell.piece!!.level == 1 -> 20
                else -> 30
            }
        }
        return expentGold
    }

    fun findShortestPath(origin: Cell, target: Cell): List<Cell> {
        val shortestPath: MutableList<Cell> = mutableListOf()
        getFlattenBoard().filterNotNull().filter { !myCells().contains(it) }.sortedBy { it.distance(target) }
            .forEach { cell ->
                if (shortestPath.isEmpty()) {
                    shortestPath += cell.copy()
                } else if (shortestPath.last().distance(cell) == 1) {
                    shortestPath += cell.copy()
                    if (cell.distance(origin) == 1)
                        return shortestPath
                }
            }
        return listOf<Cell>()
    }

    fun reconstruct(cameFrom: MutableMap<Cell, Cell>, current: Cell): MutableList<Cell?> {
        var last = current
        val path = mutableListOf<Cell?>(last)

        while (cameFrom.containsKey(last)) {
            last = cameFrom.getValue(last)
            path += last
        }
        return path
    }

    fun computeAttackPath(start: Cell, goal: Cell): MutableList<Cell?> {
        val closedSet: MutableSet<Cell> = mutableSetOf()

        val openSet: MutableSet<Cell> = mutableSetOf(start)

        val cameFrom: MutableMap<Cell, Cell> = mutableMapOf()

        val gScore: MutableMap<Cell, Int> = mutableMapOf<Cell, Int>().withDefault { Int.MAX_VALUE }
        gScore[start] = 0

        val fScore: MutableMap<Cell, Int> = mutableMapOf<Cell, Int>().withDefault { Int.MAX_VALUE }
        fScore[start] = start.distance(goal) * 10

        while (openSet.isNotEmpty()) {
            val currentM = fScore.minBy { it.value }!!
            val current = currentM.key

            if (current == goal) {
                return reconstruct(cameFrom, current)
            }

            openSet.remove(current)
            closedSet.add(current)

            lookAround(current)
                .filter { closedSet.contains(it) }
                .forEach {
                    fun cost(c: Cell) = when {
                        c.piece == null -> 0
                        c.piece!!.level > 0 -> 20
                        else -> 30
                    }

                    val candidateScore = gScore[current]!!.plus(cost(it))

                    if (!openSet.contains(it)) {
                        openSet.add(it)
                    } else if (candidateScore >= gScore.getValue(it)) {
                        return@forEach
                    }

                    cameFrom.put(it, current)
                    gScore.put(it, candidateScore)
                    fScore.put(it, gScore.getValue(it) + cost(it) + it.distance(goal) * 10)

                }
        }
        return mutableListOf<Cell?>()
    }
}

interface Action
data class MoveAction(val id: Int, val newX: Int, val newY: Int) : Action {
    override fun toString() = "MOVE $id $newX $newY"
}

data class TrainAction(val level: Int, val newX: Int, val newY: Int) : Action {
    override fun toString() = "TRAIN $level $newX $newY"
}

data class BuildAction(val structure: String, val newX: Int, val newY: Int) : Action {
    override fun toString() = "BUILD $structure $newX $newY"
}

fun parseBoard(input: Scanner): MutableList<MutableList<Cell?>> {
    return MutableList(12) { y ->
        input.next().mapIndexed { x: Int, ch ->
            val type = when (ch) {
                '#' -> null
                '.' -> 0
                'O' -> 2
                'o' -> 1
                'X' -> -2
                'x' -> -1
                else -> throw Exception("Unexpected")
            }
            type?.let { Cell(x, y, it) }
        }.toMutableList()
    }
}

fun main(args: Array<String>) {
    var debug = false
    var input = Scanner(System.`in`)

    if (args.isNotEmpty() && args[0] == "-d") {
        debug = true
        input = Scanner(File("inputStream"))
    }

    var mineSpots = List(input.nextInt()) {
        val x = input.nextInt()
        val y = input.nextInt()
        Mine(x, y, 0)
    }

    do {
        var gold = input.nextInt()
        val income = input.nextInt()
        val opponentGold = input.nextInt()
        val opponentIncome = input.nextInt()

        val board: Board = Board(parseBoard(input))

        // Parser de construções
        board.parseBuildings(input)

        // Parser de unidades
        board.parseUnits(input)

        val actions = mutableListOf<Action>()

        fun execShortestPath(shortestPath: List<Cell>) {
            shortestPath.reversed().forEach {
                when {
                    it.piece == null && board.lookAround(it).any { it.piece?.type == Board.TOWER } -> {
                        actions += TrainAction(3, it.x, it.y)
                        gold -= 30
                    }
                    it.piece == null -> {
                        actions += TrainAction(1, it.x, it.y)
                        gold -= 10
                    }

                    it.piece!!.level == 1 -> {
                        actions += TrainAction(2, it.x, it.y)
                        gold -= 20
                    }
                    else -> {
                        actions += TrainAction(3, it.x, it.y)
                        gold -= 30
                    }
                }
            }
        }


        // Estrategia
        run {
            /* Tal qual no xadrez, o desenvolvimento das peças é
            * é parte fundamental da estratégia. Aplicando este conceito,
            * o desenvolvimento será aqui considerado como número de casas dominadas
            * */

            mineSpots.filter { board.getCell(it.x, it.y)!!.ownership == 2 && board.getCell(it.x, it.y)!!.piece == null }
                .forEach { newMine ->
                    if (gold * 0.3 > 20) {
                        actions += BuildAction("MINE", newMine.x, newMine.y)
                        gold -= 20
                    }
                }
                
            // Mover todas as peças
            board.myCells()
                .filter { it.piece != null }
                .filter { it.piece!!.type == Board.UNIT }
                .forEach { cell ->
                    val winPath = board.findShortestPath(cell, board.enemyHQ)
                    if (board.evaluatePath(winPath) < gold) {
                        execShortestPath(winPath)
                    }
                    when {
                        // Foco de peças nível 1 é explorar
                        cell.piece!!.level == 1 -> {
                            val around = board.lookAround(cell)
                            val enemyEmptyCells = around.filter { it.piece == null && it.ownership < 0 }
                            val enemyCells = around.filter { it.piece != null && it.ownership < 0 }
                            val friendEmptyCells = around.filter { it.piece == null && it.ownership > 0 }
                            val unknownCells = around.filter { it.piece == null && it.ownership == 0 }
                            when {
                                unknownCells.isNotEmpty() -> {
                                    val target = unknownCells.minBy { it.distance(board.enemyHQ) }!!
                                    actions += MoveAction(cell.piece!!.id, target.x, target.y)
                                    target.piece = cell.piece
                                    cell.piece = null

                                    val winPath = board.findShortestPath(target, board.enemyHQ)
                                    if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                        execShortestPath(winPath)
                                    }

                                }
                                enemyEmptyCells.isNotEmpty() -> {
                                    val target = enemyEmptyCells.minBy { it.distance(board.enemyHQ) }!!
                                    actions += MoveAction(cell.piece!!.id, target.x, target.y)
                                    target.piece = cell.piece
                                    cell.piece = null

                                    val winPath = board.findShortestPath(target, board.enemyHQ)
                                    if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                        execShortestPath(winPath)
                                    }

                                }
                                enemyCells.isNotEmpty() -> { }
                                friendEmptyCells.isNotEmpty() -> {
                                    val target = friendEmptyCells.minBy { it.distance(board.enemyHQ) }!!
                                    actions += MoveAction(cell.piece!!.id, target.x, target.y)
                                    target.piece = cell.piece
                                    cell.piece = null
                                    val winPath = board.findShortestPath(target, board.enemyHQ)
                                    if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                        execShortestPath(winPath)
                                    }
                                }
                            }
                        }
                        // Foco de peças maiores é tomar as menores
                        cell.piece!!.level == 2 -> {
                            val target = board.lookAround(cell)
                                .filter { it.piece?.isFriendly == false && it.piece?.level == 1 || it.piece == null }
                                .minBy { it.distance(board.enemyHQ) }

                            if (target != null) {
                                actions += MoveAction(cell.piece!!.id, target.x, target.y)
                                target.piece = cell.piece
                                cell.piece = null
                                val winPath = board.findShortestPath(target, board.enemyHQ)
                                if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                    execShortestPath(winPath)
                                }
                            }
                        }
                        cell.piece!!.level == 3 -> {
                            val target = board.lookAround(cell)
                                .filter {
                                    it.piece?.isFriendly == false
                                            && (it.piece?.level == 1 || it.piece?.level == 2)
                                            || it.piece == null
                                }
                                .minBy { it.distance(board.enemyHQ) }

                            if (target != null) {
                                actions += MoveAction(cell.piece!!.id, target.x, target.y)
                                target.piece = cell.piece
                                cell.piece = null
                                val winPath = board.findShortestPath(target, board.enemyHQ)
                                if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                    execShortestPath(winPath)
                                }
                            }
                        }
                    }
                }

            System.err.println("GOLD AFTER MOVE: $gold")

            board.myCells().filter { it.piece == null }.forEach { cell ->
                val enemies = board.lookAround(cell)
                    .filter { it.piece != null }
                    .filter { it.ownership == -2 && it.piece!!.level < 3 }

                if (enemies.isNotEmpty() && gold > 15) {
                    actions += BuildAction("TOWER", cell.x, cell.y)
                    gold -= 15
                    System.err.println("Gold: $gold")
                    cell.piece = Piece(Int.MAX_VALUE, true, 1, Board.TOWER)
                }
            }

            System.err.println("GOLD AFTER DEF: $gold")

            // Encontrar fronteira e treinar soldados exaustivamente.
            board.getFlattenBoard()
                .filterNotNull()
                .filter {cell -> cell.ownership <= 0 && board.lookAround(cell).any { it.ownership == 2 } }
                .sortedBy { it.piece?.level }
                .forEach { cell ->
                    System.err.println("Frontier (x, y): (${cell.x}, ${cell.y})")
                    if (gold > 10 && cell.piece == null && board.lowerPieces().size < 3) {
                        actions += TrainAction(1, cell.x, cell.y)
                        gold -= 10

                        System.err.println("Gold after lvl 1 train: $gold")

                        cell.piece = Piece(board.lastId++, true, 1, Board.UNIT)
                        val winPath = board.findShortestPath(cell, board.enemyHQ)
                        if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                            execShortestPath(winPath)
                        }
                    } else if (gold > 21) {
                        actions += TrainAction(2, cell.x, cell.y)
                        gold -= 20
                        System.err.println("Gold after lvl 2 train: $gold")
                        cell.piece = Piece(board.lastId++, true, 2, Board.UNIT)
                        val winPath = board.findShortestPath(cell, board.enemyHQ)
                        if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                            execShortestPath(winPath)
                        }

                    }

                    if (cell.piece != null) {
                        System.err.println("Found piece on frontier!!")
                        if (gold > 21 && cell.piece!!.level == 1) {
                            System.err.println("Lvl 2 spawn at ${cell.x} ${cell.y}")
                            actions += TrainAction(2, cell.x, cell.y)
                            gold -= 20
                            cell.piece = Piece(board.lastId++, true, 2, Board.UNIT)
                            val winPath = board.findShortestPath(cell, board.enemyHQ)
                            if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                execShortestPath(winPath)
                            }
                        } else if (gold > 30 && (cell.piece!!.level > 1
                                    || cell.piece!!.type == Board.TOWER
                                    || board.lookAround(cell).any {cell.piece!!.type == Board.TOWER})) {
                            System.err.println("Lvl 3 on x: ${cell.x} ${cell.y}")
                            actions += TrainAction(3, cell.x, cell.y)
                            gold -= 30
                            cell.piece = Piece(board.lastId++, true, 3, Board.UNIT)
                            val winPath = board.findShortestPath(cell, board.enemyHQ)
                            if (board.evaluatePath(winPath) < gold && winPath.isNotEmpty()) {
                                execShortestPath(winPath)
                            }
                        }
                    }
                }
        }


        if (actions.any()) {
            println(actions.joinToString(";"))
        } else {
            println("WAIT")
        }
    } while (!debug)
}
