package com.example.ai.model

enum class TransactionSource(val wireName: String) {
    VOICE("voice"),
    TYPED("typed"),
    OCR("ocr"),
    IMPORTED("imported"),
    SMS("sms")
}
