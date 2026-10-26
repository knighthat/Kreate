---
paths:
  - "app/src/main/res/drawable*/**"
---

# Drawables

- Generated drawables (vector icons) use `#000` as their color unless the image needs a specific
  color, and are 24dp × 24dp unless another size is asked for.
- Start with the GPL XML comment header (copy `check.xml`).
- An illustration's outline is `#000`; the inside keeps its own neutral colors.
- For Material icons, take the exact `pathData` from
  `https://raw.githubusercontent.com/google/material-design-icons/master/android/<category>/<name>/materialicons/black/res/drawable/baseline_<name>_24.xml`
  instead of writing paths from memory.
