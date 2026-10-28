local path = (...) or "/"
local target = shell.resolve(path)
local ok, isDir = pcall(fs.isDir, target)
if (not ok or not isDir) and tostring(path):match("^%d+$") then
  local mountedDrive = "nas/" .. path
  local mountedOk, mountedDir = pcall(fs.isDir, mountedDrive)
  if mountedOk and mountedDir then
    target = mountedDrive
    ok, isDir = true, true
  end
end
if not ok or not isDir then
  print("cd: " .. tale.tr("directory not found: ") .. path)
  return
end
shell.setDir(target == "" and "/" or target)
