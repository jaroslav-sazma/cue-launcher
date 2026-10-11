package app.cue.launcher.engine

fun replyCandidateIndex(candidates: List<ReplyCandidate>): Int? {
    val withInput = candidates.withIndex().filter { (_, candidate) -> candidate.hasFreeFormInput }
    val semantic = withInput.firstOrNull { (_, candidate) -> candidate.isReplySemantic }
    return (semantic ?: withInput.firstOrNull())?.index
}

fun replyDraft(text: String): String? = text.trim().takeIf(String::isNotEmpty)

data class ReplyCandidate(
    val hasFreeFormInput: Boolean,
    val isReplySemantic: Boolean = false,
    val hint: String? = null,
)
