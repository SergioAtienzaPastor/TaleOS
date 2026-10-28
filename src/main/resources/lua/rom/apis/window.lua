-- Clipped, redirectable terminal windows. Cell operations bypass the parent's
-- cursor, so wrapping or scrolling a window never moves or scrolls its parent.
local window = {}

local function integer(value, name)
  if type(value) ~= "number" or value % 1 ~= 0 then
    error(name .. " must be an integer", 3)
  end
  return value
end

function window.create(parent, x, y, width, height, visible)
  if type(parent) ~= "table" or type(parent.write) ~= "function" then
    error("window.create expects a terminal parent", 2)
  end
  parent = parent.__terminalRedirect or parent
  if type(parent.__getCell) ~= "function" or type(parent.__setCell) ~= "function" then
    error("window.create parent does not expose terminal cells", 2)
  end
  x = integer(x, "x")
  y = integer(y, "y")
  width = integer(width, "width")
  height = integer(height, "height")
  if width <= 0 or height <= 0 then
    error("window dimensions must be positive", 2)
  end
  if visible == nil then visible = true end

  local object = {}
  local cursorX, cursorY = 1, 1
  local blink = false
  local textColor = parent.getTextColor and parent.getTextColor() or 32768
  local backgroundColor = parent.getBackgroundColor and parent.getBackgroundColor() or 1

  local function inside(px, py)
    return px >= 1 and px <= width and py >= 1 and py <= height
  end

  local function parentPosition(px, py)
    return x + px - 1, y + py - 1
  end

  function object.__getCell(px, py)
    if not visible or not inside(px, py) then
      return " ", textColor, backgroundColor
    end
    local parentX, parentY = parentPosition(px, py)
    return parent.__getCell(parentX, parentY)
  end

  function object.__setCell(px, py, character, fg, bg)
    if visible and inside(px, py) then
      local parentX, parentY = parentPosition(px, py)
      parent.__setCell(parentX, parentY, character, fg, bg)
    end
  end

  local function advance()
    cursorX = cursorX + 1
    if cursorX > width then
      cursorX = 1
      cursorY = cursorY + 1
      if cursorY > height then
        cursorY = height
        object.scroll(1)
      end
    end
  end

  local function newLine()
    cursorX = 1
    cursorY = cursorY + 1
    if cursorY > height then
      cursorY = height
      object.scroll(1)
    end
  end

  local function writeCharacter(character, fg, bg)
    if character == "\n" then
      newLine()
    elseif character == "\r" then
      cursorX = 1
    elseif character == "\t" then
      local spaces = 4 - (cursorX - 1) % 4
      for _ = 1, spaces do writeCharacter(" ", fg, bg) end
    else
      object.__setCell(cursorX, cursorY, character, fg, bg)
      advance()
    end
  end

  function object.write(value)
    local text = tostring(value)
    for index = 1, #text do
      writeCharacter(text:sub(index, index), textColor, backgroundColor)
    end
  end

  function object.blit(text, foreground, background)
    text = tostring(text)
    if #text ~= #foreground or #text ~= #background then
      error("blit text and color strings must have equal lengths", 2)
    end
    for index = 1, #text do
      local fg = 2 ^ tonumber(foreground:sub(index, index), 16)
      local bg = 2 ^ tonumber(background:sub(index, index), 16)
      writeCharacter(text:sub(index, index), fg, bg)
    end
  end

  function object.clear()
    for py = 1, height do
      for px = 1, width do
        object.__setCell(px, py, " ", textColor, backgroundColor)
      end
    end
    cursorX, cursorY = 1, 1
  end

  function object.clearLine()
    for px = 1, width do
      object.__setCell(px, cursorY, " ", textColor, backgroundColor)
    end
    cursorX = 1
  end

  function object.scroll(lines)
    lines = integer(lines, "lines")
    if lines == 0 then return end
    local cells = {}
    for py = 1, height do
      cells[py] = {}
      for px = 1, width do
        local character, fg, bg = object.__getCell(px, py)
        cells[py][px] = { character, fg, bg }
      end
    end
    for py = 1, height do
      for px = 1, width do
        local source = cells[py + lines]
        if source then
          local cell = source[px]
          object.__setCell(px, py, cell[1], cell[2], cell[3])
        else
          object.__setCell(px, py, " ", textColor, backgroundColor)
        end
      end
    end
  end

  function object.getSize()
    return width, height
  end

  function object.setCursorPos(px, py)
    px = integer(px, "x")
    py = integer(py, "y")
    if not inside(px, py) then error("cursor outside window", 2) end
    cursorX, cursorY = px, py
  end

  function object.getCursorPos()
    return cursorX, cursorY
  end

  function object.setCursorBlink(value)
    blink = not not value
  end

  function object.getCursorBlink()
    return blink
  end

  function object.setTextColor(color)
    textColor = color
  end

  function object.getTextColor()
    return textColor
  end

  function object.setBackgroundColor(color)
    backgroundColor = color
  end

  function object.getBackgroundColor()
    return backgroundColor
  end

  function object.isColor()
    return parent.isColor()
  end

  function object.setVisible(value)
    visible = not not value
  end

  function object.isVisible()
    return visible
  end

  function object.reposition(newX, newY, newWidth, newHeight)
    x = integer(newX, "x")
    y = integer(newY, "y")
    width = newWidth == nil and width or integer(newWidth, "width")
    height = newHeight == nil and height or integer(newHeight, "height")
    if width <= 0 or height <= 0 then error("window dimensions must be positive", 2) end
    cursorX, cursorY = 1, 1
  end

  return object
end

return window
