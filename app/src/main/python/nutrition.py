"""Lightweight offline nutrition analysis used by the Android app through Chaquopy."""


def analyze(calories: int, protein: int, carbs: int, fat: int) -> str:
    """Return a simple, non-medical coaching hint based on today's totals."""
    if calories == 0:
        return "Log a meal to start seeing your daily nutrition picture."
    notes = []
    if protein < 50:
        notes.append("Consider adding a protein-rich food to a later meal.")
    if carbs == 0:
        notes.append("Add meals to see your carbohydrate total.")
    if fat == 0:
        notes.append("Healthy fats can be part of a balanced eating pattern.")
    remaining = max(0, 2000 - calories)
    if remaining:
        notes.append(f"About {remaining} kcal remain to the app's general 2,000 kcal reference.")
    else:
        notes.append("You have reached the app's general 2,000 kcal reference.")
    return " ".join(notes)
