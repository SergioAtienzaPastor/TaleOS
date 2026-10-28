local lengthText = ...
local length = tonumber(lengthText)

if not turtle then
  print(tale.tr("excavate: turtle API not available"))
  return
end

if not length or length <= 0 or length % 1 ~= 0 then
  print(tale.tr("excavate: length must be a positive integer"))
  return
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

local function check(result, reason)
  if not result then
    print("excavate: " .. tostring(reason or tale.tr("action blocked")))
    return false
  end
  return true
end

print(tale.tr("Excavating 1x3 face..."))
for section = 1, length do
  if not check(dig("")) then return end
  while not turtle.forward() do
    if not check(dig("")) then return end
    turtle.attack()
    tale.sleep(2)
  end
  if not check(dig("Up")) then return end
  if not check(dig("Down")) then return end
end
print(tale.tr("Excavation completed: ") .. length .. tale.tr(" sections"))
