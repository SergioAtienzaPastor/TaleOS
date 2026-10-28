local path = (...) or "/"
local target = shell.resolve(path)
local ok, isDir = pcall(fs.isDir, target)
if not ok or not isDir then
  print("cd: no existe el directorio: " .. path)
  return
end
shell.setDir(path)
