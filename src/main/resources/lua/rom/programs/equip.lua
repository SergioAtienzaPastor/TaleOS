local side = ...
local method = side == "left" and "equipLeft" or side == "right" and "equipRight"

if not turtle or not method then
  print(tale.tr("usage: equip <left|right>"))
  return
end

local ok, reason = turtle[method]()
if not ok then
  print("equip: " .. tostring(reason or tale.tr("could not equip")))
end
