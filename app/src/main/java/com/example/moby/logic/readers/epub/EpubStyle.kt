package com.example.moby.logic.readers.epub

import androidx.compose.ui.graphics.Color
import com.example.moby.ui.screens.ReaderTheme

fun ReaderTheme.toColor() = when (this) {
    ReaderTheme.ARRECIFE -> Color(0xFFF8F9FA)
    ReaderTheme.CRETA    -> Color(0xFFF4ECD8)
    ReaderTheme.PAPIRUS  -> Color(0xFFD2D2D2)
    ReaderTheme.ABISAL   -> Color(0xFF011627)
}

fun ReaderTheme.toBgHex() = when (this) {
    ReaderTheme.ARRECIFE -> "#F8F9FA"
    ReaderTheme.CRETA    -> "#F4ECD8"
    ReaderTheme.PAPIRUS  -> "#D2D2D2"
    ReaderTheme.ABISAL   -> "#011627"
}

fun ReaderTheme.toTextHex() = when (this) {
    ReaderTheme.ARRECIFE -> "#2C3E50"
    ReaderTheme.CRETA    -> "#423425"
    ReaderTheme.PAPIRUS  -> "#1A1A1A"
    ReaderTheme.ABISAL   -> "#D0D0D0"
}

fun ReaderTheme.toLinkHex() = when (this) {
    ReaderTheme.ABISAL -> "#7EC8E3"
    else               -> "#2980B9"
}
