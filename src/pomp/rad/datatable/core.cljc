(ns pomp.rad.datatable.core
  "Public API for the datatable component.

   This namespace provides the main entry points for using the datatable:
   - `render` and `render-skeleton` for rendering tables
   - `next-state` and `query` for state management
   - `query-fn` for creating in-memory query functions

   Customization:
   The `render` function accepts `:render-row` and `:render-header` options
   to override the default rendering. See the `render` docstring for details.

   For more control, you can require the sub-namespaces directly:
   - `pomp.rad.datatable.state.*` for state transition functions
   - `pomp.rad.datatable.ui.*` for rendering functions
   - `pomp.rad.datatable.ui.row` for default row/cell render functions
   - `pomp.rad.datatable.query.*` for query implementations")
