# Charts

Charts use Vico (`com.patrykandpatrick.vico:compose`, version in the catalog), not its `-m3`
module: every chart passes the theme's colors itself. Statistics' charts live in
`ui/screen/account/StatisticsCharts.kt`.

## Vico pitfalls (3.3.1)

- An axis `CartesianValueFormatter` must never return a blank string: Vico throws
  (`IllegalStateException` while measuring) and the app crashes. Label every x and pick which labels
  show with `HorizontalAxis.ItemPlacer.aligned(spacing = …)`.
- A pie slice that goes all the way round (one entry) fills the donut's hole: the slice path is
  copied without its even-odd fill. `SourcesPie` draws a lone entry as two halves of one color with
  no spacing.
- A pie chart has no tap or marker; give it a legend with the names and values.
- Vico draws its own components on a canvas, never a composable: covers can't go through
  `Thumbnail` (`covers.md`), so a chart with pictures (top artists) is drawn in Compose instead.

## Conventions

- One series in the theme's primary; a color follows the entity, never its rank (`SourcesPie`
  maps each origin to a fixed color). Names and values beside a color, never color alone.
- Columns: rounded data ends (4dp), small gaps, faint grid and axis lines (`outlineVariant`).
- A tap shows a column's value (Vico marker with `rememberToggleOnTap`, or the row's own text).
- A time axis steps evenly by round minutes or hours (`timeTicks`); bars must keep a linear scale.
