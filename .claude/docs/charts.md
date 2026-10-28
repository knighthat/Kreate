# Charts

Charts use Vico (`com.patrykandpatrick.vico:compose`, version in the catalog), not its `-m3`
module: every chart passes the theme's colors itself. Statistics' charts live in
`ui/screen/account/StatisticsCharts.kt`.

## Vico pitfalls (3.3.1)

- An axis `CartesianValueFormatter` must never return a blank string: Vico throws
  (`IllegalStateException` while measuring) and the app crashes. Label every x and pick which labels
  show with `HorizontalAxis.ItemPlacer.aligned(spacing = …)`.
- A pie slice that goes all the way round (one entry) fills the donut's hole: the slice path is
  copied without its even-odd fill. A donut with a lone entry has to draw it as two halves of one
  color with no spacing (`SourcesPie` is a whole pie, so it doesn't).
- `PieChart.SliceLabel.Inside` sits at half the ring's thickness, never turns, and ellipsizes to the
  slice's chord there (`9…`). `SourcesPie` draws its labels itself (`drawSliceLabels`): across, else
  along the slice's middle, else left out.
- A pie chart has no tap or marker; give it a legend with the names and values.
- Vico draws its own components on a canvas, never a composable: covers can't go through
  `Thumbnail` (`covers.md`), so a chart with pictures (top artists) is drawn in Compose instead.

## Conventions

- One series in the theme's primary (other users' part of a size bar: the same color fainter).
- Parts that stand for different things take fixed colors from `MaterialTheme.chartColors`
  (`ui/theme/ChartColors.kt`), never the theme's primary/secondary/tertiary: those share one seed
  and are all white in pitch black. Light and dark have their own steps, checked against
  color-blindness in their order (neighbors apart); text on a part uses `Color.onChart()`. A color
  follows the entity, never its rank (`SourcesPie`: YouTube orange, OpenSubsonic blue, local aqua;
  the device storage bar: its parts in `StoragePart` order take the palette in order, 2dp gaps,
  legend in two equal columns). Names and values beside a color, never color alone.
- Columns: rounded data ends (4dp), small gaps, faint grid and axis lines (`outlineVariant`).
- A tap shows a column's value (Vico marker with `rememberToggleOnTap`, or the row's own text).
- A time axis steps evenly by round minutes or hours (`timeTicks`); bars must keep a linear scale.
  Its ticks are plain numbers; the unit (`unit_minute`/`unit_hour`) reads vertically beside them.
- A pie shows each share on its slice; its legend gives the name and value, not the share.
