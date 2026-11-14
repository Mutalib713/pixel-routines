package com.mosman.routines.ai

/**
 * One place for the cloud model's name. Google renames and retires Gemini models often (the
 * 2.5 models were being redirected from 1 Dec 2026), so never scatter the name through the code.
 * Free tier: prompts may be used by Google and read by reviewers (PROFILE.md Sacred Rule 6).
 * [NEEDS: confirm this name against firebase.google.com/docs/ai-logic/models when the
 * online assistant is built; it was read from that page on 2026-11-14.]
 */
object GeminiConfig {
    const val MODEL = "gemini-3.5-flash-lite"
}
