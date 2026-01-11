# Scenarios

## Filters

### Scenario 1

Given that I click filter on :column-name

  | :column-name |
  |--------------|
  | Name         |

When I select the :filter-type

  | :filter-type |
  |--------------|
  | contains     |

And I type in a value that should include some but not all rows

And I apply the filter

Then the rows should be filtered correctly

And the indicator of the number of rows on the page should be correct

And the forward button should make sense (e.g., be disabled if there is no page to navigate forward to)

When I click to clear the filter

Then all the rows reappear

And when I click to the next page

Then it should work as expected (e.g., it should not re-apply the filters)

### Scenario 2

Given that I click filter on :column-name

  | :column-name |
  |--------------|
  | Name         |

When I select the :filter-type

  | :filter-type |
  |--------------|
  | contains     |

And I type in a value that should include some but not all rows

And instead of applying the filter, I click outside of the filter menu

Then the filter should close, and the filter should not be applied

And I should be able to click to the next page as expected

### Scenario 3

When I navigate backwards and forwards

Then the rows should update as expected

### Scenario 4

When I click on a column

Then it should sort the rows

When I click the same column again, it should sort them the other direction

When I click the same column again, the sort should be turned off

### Scenario 5

When I click on a column that has group by enabled

Then it should group the rows by that column

When I click grouped columns options, it should ungroup the rows

### Scenario 6

I should be able to hide and unhide columns

## Editable Cells

### Scenario 7: Enter edit mode

Given that the Name column is editable

When I double-click on a cell in the Name column

Then the cell should enter edit mode with an input field

And the input should contain the current value

### Scenario 8: Save on Enter

Given that I am editing a cell

When I change the value and press Enter

Then the value should be saved

And the cell should exit edit mode

And when I reload the page, the new value should persist

### Scenario 9: Cancel on Escape

Given that I am editing a cell

When I change the value and press Escape

Then the edit should be cancelled

And the original value should be displayed

### Scenario 10: Save on blur

Given that I am editing a cell

When I change the value and click outside the input

Then the value should be saved

And the cell should exit edit mode

### Scenario 11: Non-editable cells

Given that the Century column is not editable

When I double-click on a cell in the Century column

Then the cell should NOT enter edit mode
