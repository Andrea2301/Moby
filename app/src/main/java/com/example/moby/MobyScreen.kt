package com.example.moby

sealed class MobyScreen(val name: String) {
    object Home : MobyScreen("Home")
    object Recent : MobyScreen("Recent")
    object Library : MobyScreen("Library")
    object Bookmarks : MobyScreen("Bookmarks")
    object Journal : MobyScreen("Journal")
    data class Reader(val publicationId: String) : MobyScreen("Reader")
}
