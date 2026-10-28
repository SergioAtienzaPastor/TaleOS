local path = (...) or "/"
local target = shell.resolve(path)
local ok, isDir = pcall(fs.isDir, target)
if not ok or not isDir then
  print("cd: " .. tale.tr("directory not found: ") .. path)
  return
end
shell.setDir(path)
