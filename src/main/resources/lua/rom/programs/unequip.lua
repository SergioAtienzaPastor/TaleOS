local side = ...
local method = side == "left" and "equipLeft" or side == "right" and "equipRight"

if not turtle or not method then
  print(tale.tr("usage: unequip <left|right> (select an empty slot first)"))
  return
end

local ok, reason = turtle[method]()
if not ok then
  print("unequip: " .. tostring(reason or tale.tr("could not unequip")))
end
