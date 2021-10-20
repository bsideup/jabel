package com.example;

import com.github.bsideup.jabel.Desugar;

@Desugar
record Browser(String name, boolean headless) {
}