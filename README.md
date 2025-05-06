# Capacitive Pattern Recognition (Bachelor's Thesis Project)

This repository contains the Android-based recognition system developed as part of my bachelor's thesis on capacitive keys. The app detects and verifies touch patterns created by passive, conductive tangibles on capacitive screens.

## Branch Used in Evaluation

> **Please note:** The branch used for the final evaluation is [`single-point`](https://github.com/devfab456/capkeys_recognition/tree/single-point).  
> It supports detection of a single simultaneous touch point per frame, optimized for the tangible keys developed in this project.

## Features

- Real-time pattern recognition from capacitive touch input
- Recording and verification of patterns
- Brute-force protection and lockout mechanism
- Modular architecture (`TouchProcessor`, `PatternCheck`, `PatternStorage`)

## Requirements

- Android Studio (API level 21+)
- Kotlin
- A touchscreen device

