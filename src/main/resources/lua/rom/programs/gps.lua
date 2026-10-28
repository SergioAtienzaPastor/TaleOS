local action = ...
if action == "host" then
  local x, y, z = select(2, ...)
  x, y, z = tonumber(x), tonumber(y), tonumber(z)
  if not x or not y or not z then
    print(tale.tr("Usage: gps host <x> <y> <z>"))
    return
  end
  print(tale.tr("GPS host at ") .. x .. ", " .. y .. ", " .. z .. tale.tr(" (use Stop to terminate)"))
  gps.host(x, y, z)
elseif action == "locate" or action == nil then
  local x, y, z = gps.locate()
  if x then
    print(string.format("%.3f %.3f %.3f", x, y, z))
  else
    print("gps: " .. tostring(y))
  end
else
  print(tale.tr("Usage: gps [locate] | gps host <x> <y> <z>"))
end
