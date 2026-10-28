local lengthText = ...
local length = tonumber(lengthText)

if not turtle then
  print("excavate: turtle API not available")
  return
end

if not length or length <= 0 or length % 1 ~= 0 then
  print("excavate: length must be a positive integer")
  return
end

local function dig(direction)
  local detect = direction == "" and turtle.detect or turtle["detect" .. direction]
  local digBlock = direction == "" and turtle.dig or turtle["dig" .. direction]
  while detect() do
    local ok, reason = digBlock()
    if not ok then
      return nil, reason or "no se pudo excavar"
    end
    tale.sleep(2)
  end
  return true
end

local function check(result, reason)
  if not result then
    print("excavate: " .. tostring(reason or "acción bloqueada"))
    return false
  end
  return true
end

print("Excavando frente de 1x3...")
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
print("Excavación completada: " .. length .. " secciones")
