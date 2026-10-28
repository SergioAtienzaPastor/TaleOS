local path = ...
if not path then
  print(tale.tr("Usage: mkdir <directory>"))
  return
end
local ok, err = pcall(fs.mkdir, path)
if not ok then
  print("mkdir: " .. tostring(err))
end
