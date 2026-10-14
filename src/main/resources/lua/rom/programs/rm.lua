local path = ...
if not path then
  print("uso: rm <archivo|directorio>")
  return
end
local ok, err = pcall(fs.delete, path)
if not ok then
  print("rm: " .. tostring(err))
end
