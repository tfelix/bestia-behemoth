package net.bestia.client

import com.googlecode.lanterna.TerminalPosition
import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.TextColor
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.input.KeyType
import com.googlecode.lanterna.screen.Screen
import com.googlecode.lanterna.screen.TerminalScreen
import com.googlecode.lanterna.terminal.DefaultTerminalFactory
import com.googlecode.lanterna.terminal.swing.AWTTerminalFontConfiguration
import net.bestia.client.command.CliCommand


class CLI(
  private val commands: List<CliCommand>
) {
  private val commandNameList = commands.map { it.name }

  private val lock = Any()

  private val outputLines: MutableList<String> = mutableListOf()
  private val history: MutableList<String> = mutableListOf()
  private val currentInput = StringBuilder()
  private var historyIndex = -1
  private var needsRedraw = true
  private lateinit var terminalSize: TerminalSize

  private val bestiaVersionString = "Bestia CLI Client v" + VersionReader.version

  fun start() {
    val terminal = DefaultTerminalFactory()
      .setTerminalEmulatorTitle(bestiaVersionString)
      .createTerminal()

    val screen = TerminalScreen(terminal)
    screen.startScreen()

    terminalSize = screen.terminalSize

    printText("Bestia CLI Client v" + VersionReader.version)

    while (true) {
      val newSize = screen.doResizeIfNecessary()
      if (newSize != null) {
        terminalSize = newSize

        synchronized(lock) {
          trimLines(outputLines, terminalSize.rows)
          needsRedraw = true
        }
      }
      val keyStroke = screen.pollInput()
      if (keyStroke != null) {
        synchronized(lock) {
          when (keyStroke.keyType) {
            KeyType.EOF -> {
              screen.stopScreen()
              return
            }

            KeyType.Character -> {
              if (keyStroke.isCtrlDown && keyStroke.character == 'c') {
                screen.stopScreen()
                return
              }
              currentInput.append(keyStroke.character)
              needsRedraw = true
            }

            KeyType.Enter -> executeCommand(screen)

            KeyType.Backspace -> {
              if (currentInput.isNotEmpty()) {
                currentInput.deleteCharAt(currentInput.length - 1)
                needsRedraw = true
              }
            }

            KeyType.Tab -> {
              val match = commandNameList.stream()
                .filter { cmd: String? ->
                  cmd!!.startsWith(
                    currentInput.toString()
                  )
                }
                .findFirst()
                .orElse(null)
              if (match != null) {
                currentInput.setLength(0)
                currentInput.append(match)
                needsRedraw = true
              }
            }

            KeyType.ArrowUp -> {
              if (historyIndex > 0) {
                historyIndex--
                currentInput.setLength(0)
                currentInput.append(history[historyIndex])
                needsRedraw = true
              }
            }

            KeyType.ArrowDown -> {
              if (historyIndex < history.size - 1) {
                historyIndex++
                currentInput.setLength(0)
                currentInput.append(history[historyIndex])
                needsRedraw = true
              } else {
                historyIndex = history.size
                currentInput.setLength(0)
                needsRedraw = true
              }
            }

            else -> {}
          }
        }
      }

      // Only redraw if flagged
      if (needsRedraw) {
        synchronized(lock) {
          screen.clear()
          var row = 0
          val maxOutput = terminalSize.rows - 1
          val start = Math.max(0, outputLines.size - maxOutput)
          for (i in start until outputLines.size) {
            screen.newTextGraphics().putString(0, row++, outputLines[i])
          }
          val prompt = "> $currentInput"
          screen.newTextGraphics()
            .setForegroundColor(TextColor.ANSI.WHITE)
            .putString(0, terminalSize.rows - 1, prompt)
          screen.cursorPosition = TerminalPosition(prompt.length, terminalSize.rows - 1)
          screen.refresh()
          needsRedraw = false
        }
      }

      // Sleep a bit to avoid high CPU usage
      try {
        Thread.sleep(10)
      } catch (ignored: InterruptedException) {
      }
    }
  }

  private fun executeCommand(screen: Screen) {
    val input = currentInput.toString()

    if (input.isBlank() || input == "help") {
      printHelp()
      return
    }

    val tokens = input.trim().split(Regex("\\s+"))

    history.add(input)
    historyIndex = history.size
    currentInput.setLength(0)

    val cmd = commands.firstOrNull { it.name == tokens[0].lowercase() }

    if (cmd == null) {
      printText("Unknown command: $input")
    } else {
      try {
        cmd.execute(tokens)
      } catch (e: CLIException) {
        printText(e.message ?: "No error message provided")
      }
    }

    if (input.equals("exit", ignoreCase = true)) {
      screen.stopScreen()
      return
    }

    needsRedraw = true
  }

  fun printText(text: String) {
    synchronized(lock) {
      text.lines().forEach { line ->
        outputLines.add(line)
      }
      trimLines(outputLines, terminalSize.rows)
      needsRedraw = true
    }
  }

  private fun printHelp() {
    printText("Available commands (use help or help <command> for more info):")
    printText(commands.joinToString(", ") { it.name })
  }

  private fun trimLines(lines: MutableList<String>, terminalHeight: Int) {
    while (lines.size > terminalHeight - 1) {
      lines.removeAt(0)
    }
  }
}
