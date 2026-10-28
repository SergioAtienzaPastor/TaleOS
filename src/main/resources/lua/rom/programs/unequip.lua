local side = ...
local method = side == "left" and "equipLeft" or side == "right" and "equipRight"

if not turtle or not method then
  print("usage: unequip <left|right> (selecciona antes un slot vacío)")
  return
end

local ok, reason = turtle[method]()
if not ok then
  print("unequip: " .. tostring(reason or "no se pudo desequipar"))
end
