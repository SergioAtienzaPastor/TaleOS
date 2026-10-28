local direction, countText = ...
local count = tonumber(countText) or 1
local rotations = {
  left = turtle and turtle.turnLeft,
  right = turtle and turtle.turnRight
}

if not rotations[direction] or count < 1 or count % 1 ~= 0 then
  print("usage: turn <left|right> [count]")
  return
end

for _ = 1, count do
  local ok, reason = rotations[direction]()
  if not ok then
    print("turn: " .. tostring(reason or "no se pudo girar"))
    return
  end
end
