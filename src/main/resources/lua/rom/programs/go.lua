local direction, countText = ...
local moves = {
  forward = "forward", forwards = "forward", fd = "forward",
  back = "back", bk = "back",
  up = "up",
  down = "down", dn = "down",
  left = "turnLeft", lt = "turnLeft",
  right = "turnRight", rt = "turnRight"
}

if not turtle then
  print(tale.tr("go: turtle API not available"))
  return
end

if not moves[direction] then
  print(tale.tr("usage: go <forward|back|up|down|left|right> [count]"))
  return
end

local count = 1
if countText ~= nil then
  count = tonumber(countText)
  if not count or count <= 0 or count % 1 ~= 0 then
    print(tale.tr("go: count must be a positive integer"))
    return
  end
end

for _ = 1, count do
  local ok, reason = turtle[moves[direction]]()
  if not ok then
    print("go: " .. tostring(reason or tale.tr("movement failed")))
    return
  end
end
