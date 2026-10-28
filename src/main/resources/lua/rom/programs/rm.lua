local path = ...
if not path then
  print(tale.tr("Usage: rm <file|directory>"))
  return
end
local ok, err = pcall(fs.delete, path)
if not ok then
  print("rm: " .. tostring(err))
end
