// SPDX-License-Identifier: GPL-3.0-only
// What each command does follows SwiftSlate (github.com/Musheer360/SwiftSlate, service/AssistantService.kt), MIT
// License, Copyright (c) 2026 Musheer Alam. fork: runs inside the keyboard on the text before the cursor, instead of
// an accessibility service rewriting the whole field; progress and late results show on the dynamic toolbar.
package helium314.keyboard.fork.slate

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.prefs

/**
 * SwiftSlate style commands typed at the end of the text ("`i dont no ?fix`"). The text is everything before the
 * cursor (up to [MAX_TEXT] characters), the trigger included; the command replaces it:
 * - `?copy` / `?cut` / `?paste` / `?replace`: clipboard, offline
 * - `?undo`: back to the text before the last command
 * - text replacers: the trigger becomes the saved text
 * - AI commands: the text is sent to Gemini (optionally with Google Search) and the answer takes its place
 */
class SlateRunner(private val ime: LatinIME, private val ui: Ui) {
    /** how the dynamic toolbar shows a running command and a late result */
    interface Ui {
        fun showProgress(label: String, onCancel: () -> Unit)
        fun showResult(label: String, result: String, onInsert: () -> Unit, onDismiss: () -> Unit)
        fun hide()
    }

    private val handler = Handler(Looper.getMainLooper())
    /** the text before the last command, for ?undo (single level, like SwiftSlate) */
    private var lastOriginal: String? = null
    /** id of the AI request running, results of older (cancelled) ones are dropped */
    private var running = 0
    private var busy = false

    private val prefs get() = ime.prefs()

    /** @return true if the text before the cursor ended with a command, which is now handled */
    fun onTextChanged(): Boolean {
        if (busy || !SlateCommands.enabled(prefs)) return false
        val text = ime.forkTextBeforeCursor(MAX_TEXT)?.toString() ?: return false
        if (text.isEmpty()) return false
        // cheap check first: every trigger contains the prefix
        if (!text.contains(SlateCommands.prefix(prefs))) return false
        val command = SlateCommands.find(prefs, text) ?: return false
        val preceding = text.substring(0, text.length - command.trigger.length)
        execute(command, text, preceding)
        return true
    }

    /** run [command] on the text before the cursor, without a typed trigger (toolbar) */
    fun runOnText(command: SlateCommand) {
        if (busy) return
        val text = ime.forkTextBeforeCursor(MAX_TEXT)?.toString().orEmpty()
        execute(command, text, text)
    }

    /** run [command] on the last [tail] characters before the cursor only (translation: the current paragraph) */
    fun runOnTail(command: SlateCommand, tail: String) {
        if (busy) return
        execute(command, tail, tail)
    }

    /**
     * run an AI [command] on [text], the field's characters [start] to [end] (translation of a selection, a sentence,
     * the whole text): the answer replaces them if they are still the same, else it is offered
     */
    fun runOnRange(command: SlateCommand, start: Int, end: Int, text: String) {
        if (busy) return
        val clean = text.trim()
        if (clean.isEmpty()) return toast("변환할 글이 없어요")
        SlateCommands.countUse(prefs, command.trigger)
        busy = true
        val id = ++running
        ui.showProgress(command.trigger) {
            running++
            busy = false
            ui.hide()
        }
        val app = ime.applicationContext
        Thread {
            val outcome = GeminiClient.run(app.prefs(), command.prompt, clean, command.search)
            handler.post {
                if (id != running) return@post
                busy = false
                when (outcome) {
                    is GeminiClient.Outcome.Failure -> { ui.hide(); toast(outcome.message) }
                    is GeminiClient.Outcome.Success -> {
                        val ic = ime.currentInputConnection
                        val now = ic?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)
                        val nowText = now?.text?.toString()
                        val offset = now?.startOffset ?: 0
                        val s = start - offset
                        val e = end - offset
                        if (ic != null && nowText != null && s >= 0 && e <= nowText.length && nowText.substring(s, e) == text) {
                            ui.hide()
                            // keep the spaces around the text, the model's answer has none
                            val lead = text.takeWhile { it.isWhitespace() }
                            val trail = text.takeLastWhile { it.isWhitespace() }
                            ic.beginBatchEdit()
                            ic.finishComposingText()
                            ic.setSelection(start, end)
                            ic.commitText(lead + outcome.text + trail, 1)
                            ic.endBatchEdit()
                        } else {
                            ui.showResult(command.trigger, outcome.text, onInsert = {
                                ui.hide()
                                ime.forkReplaceBeforeCursor(0, outcome.text)
                            }, onDismiss = { ui.hide() })
                        }
                    }
                }
            }
        }.start()
    }

    private fun execute(command: SlateCommand, text: String, preceding: String) {
        SlateCommands.countUse(prefs, command.trigger)
        val name = command.trigger.removePrefix(SlateCommands.prefix(prefs))
        if (command.isBuiltIn) when {
            name == "undo" -> {
                val original = lastOriginal ?: return toast("되돌릴 게 없어요")
                replace(text, original)
                lastOriginal = preceding // toggles, like SwiftSlate
                return
            }
            name == "copy" -> {
                val copy = preceding.trim()
                if (copy.isEmpty()) return toast("복사할 글이 없어요")
                removeTrigger(text, preceding)
                setClip(copy)
                return toast("복사했어요")
            }
            name == "cut" -> {
                val cut = preceding.trim()
                if (cut.isEmpty()) return toast("잘라낼 글이 없어요")
                lastOriginal = preceding
                replace(text, "")
                setClip(cut)
                return toast("잘라냈어요")
            }
            name == "paste" || name == "replace" -> {
                val clip = clipText() ?: return toast("클립보드가 비어 있어요")
                lastOriginal = preceding
                if (name == "paste") ime.forkReplaceBeforeCursor(text.length - preceding.length, clip)
                else replace(text, clip)
                return
            }
        }
        if (command.type == CommandType.TEXT_REPLACER) {
            lastOriginal = preceding
            ime.forkReplaceBeforeCursor(text.length - preceding.length, command.prompt)
            return
        }
        // AI
        val clean = preceding.trim()
        if (clean.isEmpty()) return toast("변환할 글이 없어요")
        // the trigger goes away right away, the text stays until the answer is there
        removeTrigger(text, preceding)
        busy = true
        val id = ++running
        ui.showProgress(command.trigger) {
            running++ // drop the answer
            busy = false
            ui.hide()
        }
        val app = ime.applicationContext
        Thread {
            val outcome = GeminiClient.run(app.prefs(), command.prompt, clean, command.search)
            handler.post { onAnswer(id, command, preceding, outcome) }
        }.start()
    }

    private fun onAnswer(id: Int, command: SlateCommand, preceding: String, outcome: GeminiClient.Outcome) {
        if (id != running) return
        busy = false
        when (outcome) {
            is GeminiClient.Outcome.Failure -> {
                ui.hide()
                toast(outcome.message)
            }
            is GeminiClient.Outcome.Success -> {
                val now = ime.forkTextBeforeCursor(MAX_TEXT)?.toString()
                // the text the command ran on is still right before the cursor (for a paragraph: the end of it)
                if (now != null && now.endsWith(preceding)) {
                    // nothing was typed meanwhile: the answer takes the text's place
                    ui.hide()
                    lastOriginal = preceding
                    if (command.appendResult) replace(preceding, preceding.trimEnd() + "\n" + outcome.text)
                    else replace(preceding, keepLeadingSpace(preceding) + outcome.text)
                } else {
                    // the text changed: offer the answer instead of overwriting what was typed
                    ui.showResult(command.trigger, outcome.text, onInsert = {
                        ui.hide()
                        ime.forkReplaceBeforeCursor(0, outcome.text)
                    }, onDismiss = { ui.hide() })
                }
            }
        }
    }

    /** the text was trimmed for the AI; keep a line break or space it started with */
    private fun keepLeadingSpace(text: String) = text.takeWhile { it.isWhitespace() }

    /** only the typed trigger goes, the text before it stays as it is */
    private fun removeTrigger(text: String, preceding: String) {
        if (text.length > preceding.length) ime.forkReplaceBeforeCursor(text.length - preceding.length, "")
    }

    private fun replace(old: String, new: String) {
        ime.forkReplaceBeforeCursor(old.length, new)
    }

    private fun setClip(text: String) {
        runCatching {
            (ime.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("DT Keyboard", text))
        }.onFailure { Log.w(TAG, "can't set clipboard", it) }
    }

    private fun clipText(): String? = runCatching {
        val cm = ime.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ime)?.toString()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun toast(text: String) = KeyboardSwitcher.getInstance().showToast(text, true)

    companion object {
        private const val TAG = "SlateRunner"
        /** how much text before the cursor a command works on */
        const val MAX_TEXT = 4000
    }
}
