package com.example.shaderrippleeffect.data

data class RevealTransitionParams(
    val speed: Float = 1200f,
    val frequency: Float = 40f,
    val amplitude: Float = 30.5f,
    val edgeWidth: Float = 0f,
    val wiggleStrength: Float = 38.0f,
    val duration: Float = 3f,
    val transitionDelay: Long = 200
)