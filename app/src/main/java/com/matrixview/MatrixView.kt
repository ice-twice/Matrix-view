package com.matrixview

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*
import kotlin.math.ceil
import kotlin.math.min
import kotlin.random.Random

class MatrixView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
    defStyleRes: Int = 0
) : View(context, attrs, defStyleAttr, defStyleRes), DefaultLifecycleObserver {
    private val density = context.resources.displayMetrics.density
    private val symbolSizePx = DEFAULT_SYMBOL_SIZE_DP * density
    private var heightWithSymbolOffset = 0f
    private val animationSpeedPxPerSecond = DEFAULT_ANIMATION_SPEED_DP_PER_SECOND * density
    private var pxPerAnimationUpdate = 0f
    private var maxVisibleSymbolsInColumn = 0
    private lateinit var minMaxSymbolsInLineRange: IntRange
    private var xCoordinateOffset = 0f
    private val headSymbolChangeDelayPx = CHANGE_HEAD_SYMBOL_DELAY_DP * density
    private val changeCommonSymbolDelayPx = CHANGE_COMMON_SYMBOL_DELAY_DP * density
    private val symbolsColumns = mutableListOf<SymbolsColumn>()
    private val matrixAnimationScope = CoroutineScope(Dispatchers.Main)
    private var matrixAnimationJob: Job? = null
    private var animatedYPosition = 0f
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        textSize = symbolSizePx
    }
    private val symbolRect = Rect()
    private var isStopped = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = symbolSizePx.toInt()
        val desiredHeight = symbolSizePx.toInt()
        val width = calculateSizeValue(desiredWidth, widthMeasureSpec)
        val height = calculateSizeValue(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(width, height)
    }

    private fun calculateSizeValue(desiredSize: Int, measureSpec: Int): Int {
        val mode = MeasureSpec.getMode(measureSpec)
        val valueSize = MeasureSpec.getSize(measureSpec)
        val value = when (mode) {
            MeasureSpec.EXACTLY -> valueSize
            MeasureSpec.AT_MOST -> min(desiredSize, valueSize) // handle wrap_content
            else -> desiredSize
        }
        return MeasureSpec.makeMeasureSpec(value, measureSpec)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateProperties(w, h)
        setupColumns(w)
        stopMatrixAnimation()
        startMatrixAnimation()
    }

    private fun calculateProperties(w: Int, h: Int) {
        heightWithSymbolOffset = h + symbolSizePx
        pxPerAnimationUpdate = animationSpeedPxPerSecond / ANIMATION_UPDATES_COUNT_PER_SECOND
        maxVisibleSymbolsInColumn = ceil(heightWithSymbolOffset / symbolSizePx).toInt()
        minMaxSymbolsInLineRange =
            ceil(maxVisibleSymbolsInColumn * MIN_SYMBOLS_IN_LINE_FACTOR).toInt()..ceil(
                maxVisibleSymbolsInColumn * MAX_SYMBOLS_IN_LINE_FACTOR
            ).toInt()
        xCoordinateOffset = (w % symbolSizePx) / 2
    }

    private fun setupColumns(w: Int) {
        val columnCount = (w / symbolSizePx).toInt()
        val maxLinesInColumn =
            ceil(maxVisibleSymbolsInColumn.toFloat() / minMaxSymbolsInLineRange.first).toInt()
        repeat(columnCount) {
            val symbolsLinesArray = Array(maxLinesInColumn) { index ->
                val isEmptyLine = index % 2 == 0
                val isShown = index == 0
                if (isEmptyLine) {
                    EmptyLine(minMaxSymbolsInLineRange.last, isShown)
                } else {
                    // randomize head step
                    val headSymbolChangeDelayMin = headSymbolChangeDelayPx - density // delay - 1dp
                    val headSymbolChangeDelayMax = headSymbolChangeDelayPx + density  // delay + 1dp
                    val randomizedHeadSymbolChangeDelay =
                        headSymbolChangeDelayMin + Random.nextFloat() * (headSymbolChangeDelayMax - headSymbolChangeDelayMin)
                    SymbolsLine(
                        minMaxSymbolsInLineRange.last,
                        isShown,
                        randomizedHeadSymbolChangeDelay
                    )
                }

            }
            val symbolsColumn = SymbolsColumn(symbolsLinesArray)
            symbolsColumns.add(symbolsColumn)
        }
    }

    private fun stopMatrixAnimation() = matrixAnimationJob?.cancel()

    private fun startMatrixAnimation() {
        matrixAnimationJob = matrixAnimationScope.launch {
            while (true) {
                calculateNextAnimatedYPosition()
                updateSymbols()
                invalidate()
                delay(MS_PER_ANIMATION_UPDATE.toLong())
            }
        }
    }

    private fun calculateNextAnimatedYPosition() {
        animatedYPosition += pxPerAnimationUpdate
        if (animatedYPosition > heightWithSymbolOffset) {
            animatedYPosition %= heightWithSymbolOffset
        }
    }

    private fun updateSymbols() {
        symbolsColumns.forEach { symbolsColumn ->
            symbolsColumn.symbolsLines.forEachIndexed { lineIndex, symbolsLine ->
                if (symbolsLine.isShown) {
                    handleLine(symbolsColumn, lineIndex, symbolsLine)
                }
            }
        }
    }

    private fun handleLine(symbolsColumn: SymbolsColumn, lineIndex: Int, symbolsLine: Line) {
        for (symbolIndex in symbolsLine.startIndex..symbolsLine.endIndex) {
            val symbolData = symbolsLine.symbols[symbolIndex]

            initializeFirstLineInColumnIfNeeded(symbolsColumn, symbolsLine, symbolData)

            val y = calculateYPosition(symbolData.startedAtY)

            // show the next symbol or line
            if (symbolIndex == symbolsLine.endIndex) {
                if (symbolsLine.endIndex != symbolsLine.size - 1) {
                    showNextSymbolInLine(y, symbolsLine, symbolIndex, symbolData)
                } else {
                    showNextLine(symbolsColumn, lineIndex, y, symbolData)
                }
            }
            hideAlreadyInvisibleSymbolOrLine(y, symbolsLine)
            changeSymbolsInLine(symbolsLine)
        }
    }

    private fun initializeFirstLineInColumnIfNeeded(
        symbolsColumn: SymbolsColumn,
        symbolsLine: Line,
        symbolData: SymbolData
    ) {
        if (symbolsColumn.isStarted.not()) {
            symbolsColumn.isStarted = true

            symbolsLine.apply {
                generateAndSetNewSize()
                if (this is SymbolsLine) {
                    symbolData.generateAndSetNewSymbol()
                }
            }
        }
    }

    private fun Line.generateAndSetNewSize() {
        size = minMaxSymbolsInLineRange.random()
    }

    private fun SymbolData.generateAndSetNewSymbol() {
        symbol = (33..Char.MAX_VALUE.code).random().toChar()
    }

    private fun calculateYPosition(yPosition: Float) =
        if (animatedYPosition < yPosition) {
            heightWithSymbolOffset + animatedYPosition - yPosition
        } else {
            animatedYPosition - yPosition
        }

    private fun showNextSymbolInLine(
        y: Float,
        symbolsLine: Line,
        symbolIndex: Int,
        symbolData: SymbolData
    ) {
        if (isNextCharacterReached(y)) {
            val nextSymbolIndex = symbolIndex + 1
            updateNextSymbolByIndex(nextSymbolIndex, symbolsLine, symbolData)
            symbolsLine.endIndex = nextSymbolIndex
        }
    }

    private fun isNextCharacterReached(y: Float) = (y > symbolSizePx)

    private fun updateNextSymbolByIndex(
        nextSymbolIndex: Int,
        symbolsLine: Line,
        symbolData: SymbolData
    ) {
        val nextSymbolData = symbolsLine.symbols[nextSymbolIndex]
        if (symbolsLine is SymbolsLine) {
            nextSymbolData.generateAndSetNewSymbol()
        }
        nextSymbolData.startedAtY = calculateNextStartedAtYBy(symbolData)
    }

    private fun calculateNextStartedAtYBy(symbolData: SymbolData) =
        (symbolData.startedAtY + symbolSizePx) % heightWithSymbolOffset

    private fun showNextLine(
        symbolsColumn: SymbolsColumn,
        lineIndex: Int,
        y: Float,
        symbolData: SymbolData
    ) {
        val nextLineIndex = (lineIndex + 1) % symbolsColumn.symbolsLines.size
        val newSymbolsLine = symbolsColumn.symbolsLines[nextLineIndex]
        if (!newSymbolsLine.isShown // no need to go further if the line has already been shown
            && isNextCharacterReached(y)
        ) {
            newSymbolsLine.apply {
                generateAndSetNewSize()
                startIndex = 0
                endIndex = 0
                isShown = true
                if (this is SymbolsLine) {
                    val symbolUpdatedAt = calculateNextStartedAtYBy(symbolData)
                    headSymbolUpdateAt = symbolUpdatedAt
                    commonSymbolUpdateAt = symbolUpdatedAt
                }
            }
            updateNextSymbolByIndex(0, newSymbolsLine, symbolData)
        }
    }

    private fun hideAlreadyInvisibleSymbolOrLine(y: Float, symbolsLine: Line) {
        if (y + pxPerAnimationUpdate >= heightWithSymbolOffset) {
            symbolsLine.startIndex++
            if (symbolsLine.startIndex > symbolsLine.endIndex) {
                symbolsLine.isShown = false // hide the whole line
            }
        }
    }

    private fun changeSymbolsInLine(symbolsLine: Line) {
        if (symbolsLine is SymbolsLine
            && symbolsLine.isShown // it can be already non-shown at this moment
        ) {
            if (symbolsLine.startIndex == 0) { // change head symbol
                val y = calculateYPosition(symbolsLine.headSymbolUpdateAt)

                if (y > symbolsLine.headSymbolUpdateDelayPx) {
                    val symbolData = symbolsLine.symbols[0]
                    symbolData.generateAndSetNewSymbol()
                    symbolsLine.headSymbolUpdateAt =
                        (symbolsLine.headSymbolUpdateAt + symbolsLine.headSymbolUpdateDelayPx) % heightWithSymbolOffset
                }
            }

            // change other symbols
            if (symbolsLine.size > 1) {
                var startIndex = symbolsLine.startIndex
                if (startIndex == 0) { // skip head symbol in line
                    startIndex++
                }
                if (startIndex <= symbolsLine.endIndex) {
                    val symbolIndexToUpdate = (startIndex..symbolsLine.endIndex).random()
                    val y = calculateYPosition(symbolsLine.commonSymbolUpdateAt)
                    if (y > changeCommonSymbolDelayPx) {
                        val symbolData = symbolsLine.symbols[symbolIndexToUpdate]
                        symbolData.generateAndSetNewSymbol()
                        symbolsLine.commonSymbolUpdateAt =
                            (symbolsLine.commonSymbolUpdateAt + changeCommonSymbolDelayPx) % heightWithSymbolOffset
                    }
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas?) { // onDraw call may be dropped by the framework so the logic that is not related to drawing must be elsewhere
        super.onDraw(canvas)
        symbolsColumns.forEachIndexed { columnIndex, symbolsColumn ->
            for (symbolsLine in symbolsColumn.symbolsLines) {
                if (symbolsLine.isShown && symbolsLine is SymbolsLine) { // there is no need to draw empty lines
                    for (symbolIndex in symbolsLine.startIndex..symbolsLine.endIndex) {
                        val symbolData = symbolsLine.symbols[symbolIndex]
                        val symbolBottomY = calculateYPosition(symbolData.startedAtY)

                        symbolPaint.color = if (symbolIndex == 0) { // head symbol
                            Color.WHITE
                        } else {
                            Color.GREEN
                        }

                        // calculate alpha
                        var alpha = 255 // head is completely opaque
                        if (symbolIndex != 0) { // tail with the effect of fading
                            val symbolAlphaSubtractor =
                                (150f / (symbolsLine.size - 2) * (symbolIndex - 1)).let {
                                    if (it.isNaN()) 0.0f else it // isNaN may be if count == 2
                                }
                            alpha = (180 - symbolAlphaSubtractor).toInt()
                        }
                        symbolPaint.alpha = alpha

                        val symbol = symbolData.symbol.toString()

                        // getTextBounds is needed to center the symbol within symbolSizePx
                        symbolPaint.getTextBounds(symbol, 0, symbol.length, symbolRect)
                        val symbolWidth = symbolRect.width()
                        val symbolHeight = symbolRect.height()

                        val x =
                            (symbolSizePx - symbolWidth) / 2 - symbolRect.left + columnIndex * symbolSizePx + xCoordinateOffset
                        val y =
                            symbolBottomY - (symbolSizePx - symbolHeight) / 2 - symbolRect.bottom
                        canvas?.drawText(symbol, x, y, symbolPaint)
                    }
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (isStopped) {
            startMatrixAnimation()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        isStopped = true
        stopMatrixAnimation()
    }

    private class SymbolsColumn(val symbolsLines: Array<Line>) {
        var isStarted = false
    }

    private sealed class Line(maxSymbolsCount: Int, var isShown: Boolean) {
        val symbols = Array(maxSymbolsCount) { SymbolData(Char(0), 0f) }
        var startIndex = 0
        var endIndex = 0
        var size = 0
    }

    private class EmptyLine(maxSymbolsCount: Int, isShown: Boolean) : Line(maxSymbolsCount, isShown)

    private class SymbolsLine(
        maxSymbolsCount: Int,
        isShown: Boolean,
        var headSymbolUpdateDelayPx: Float
    ) : Line(maxSymbolsCount, isShown) {
        var headSymbolUpdateAt = 0f
        var commonSymbolUpdateAt = 0f
    }

    private data class SymbolData(var symbol: Char, var startedAtY: Float)

    private companion object {
        private const val DEFAULT_SYMBOL_SIZE_DP = 10
        private const val DEFAULT_ANIMATION_SPEED_DP_PER_SECOND = 200
        private const val CHANGE_HEAD_SYMBOL_DELAY_DP = 19
        private const val CHANGE_COMMON_SYMBOL_DELAY_DP = 21
        private const val ANIMATION_UPDATES_COUNT_PER_SECOND = 60
        private const val MIN_SYMBOLS_IN_LINE_FACTOR = 0.2
        private const val MAX_SYMBOLS_IN_LINE_FACTOR = 0.8
        private const val MS_IN_SECOND = 1000
        private const val MS_PER_ANIMATION_UPDATE =
            MS_IN_SECOND.toFloat() / ANIMATION_UPDATES_COUNT_PER_SECOND
    }
}