local lengthText = ...
local length = 1

if not turtle then
  print(tale.tr("tunnel: turtle API not available"))
  return
end

if lengthText ~= nil then
  length = tonumber(lengthText)
  if not length or length <= 0 or length % 1 ~= 0 then
    print(tale.tr("tunnel: length must be a positive integer"))
    return
  end
end

local function dig(direction)
  local detect = direction == "" and turtle.detect or turtle["detect" .. direction]
  local digBlock = direction == "" and turtle.dig or turtle["dig" .. direction]
  while detect() do
    local ok, reason = digBlock()
    if not ok then
      return nil, reason or tale.tr("could not excavate")
    end
    tale.sleep(2)
  end
  return true
end

local function move(direction)
  local fn = direction == "" and turtle.forward or turtle[direction]
  while not fn() do
    local ok, reason = dig(direction == "up" and "Up" or direction == "down" and "Down" or "")
    if not ok then
      return nil, reason
    end
    tale.sleep(2)
  end
  return true
end

local function check(result, reason)
  if not result then
    print("tunnel: " .. tostring(reason or tale.tr("movement blocked")))
    return false
  end
  return true
end

print(tale.tr("Digging 3x3 tunnel..."))
for section = 1, length do
  if not check(dig("Up")) then return end

  turtle.turnLeft()
  if not check(dig("")) then return end
  if not check(move("up")) then return end
  if not check(dig("")) then return end
  if not check(dig("Up")) then return end

  turtle.turnRight()
  turtle.turnRight()
  if not check(dig("")) then return end
  if not check(dig("Up")) then return end
  if not check(move("down")) then return end
  if not check(dig("")) then return end
  turtle.turnLeft()

  if section < length then
    if not check(dig("")) then return end
    if not check(move("")) then return end
  end
end
print(tale.tr("Tunnel completed: ") .. length .. tale.tr(" sections"))
