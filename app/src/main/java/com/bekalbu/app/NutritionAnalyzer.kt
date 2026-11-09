package com.bekalbu.app

import com.chaquo.python.Python

/** Local Python nutrition helper; works offline using the server-provided daily totals. */
object NutritionAnalyzer {
    fun analyze(summary: DailySummary): String = try {
        Python.getInstance().getModule("nutrition")
            .callAttr("analyze", summary.calories, summary.protein, summary.carbs, summary.fat)
            .toJava(String::class.java)
    } catch (error: Exception) {
        "Nutrition analysis is temporarily unavailable."
    }
}
