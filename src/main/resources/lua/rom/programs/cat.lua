local path = ...
if not path then
  print(tale.tr("Usage: cat <file>"))
  return
end
local content = fs.read(path)
if not content then
  print("cat: " .. tale.tr("not found: ") .. path)
  return
end
print(content)
