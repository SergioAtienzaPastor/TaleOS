local path = ...
if not path then
  print("uso: mkdir <directorio>")
  return
end
local ok, err = pcall(fs.mkdir, path)
if not ok then
  print("mkdir: " .. tostring(err))
end
