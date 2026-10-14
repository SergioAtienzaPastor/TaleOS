local path = (...) or ""
local ok, names = pcall(fs.list, path)
if not ok then
  print("ls: " .. tostring(names))
  return
end
for _, n in ipairs(names) do
  local full = path == "" and n or path .. "/" .. n
  local suffix = fs.isDir(full) and "/" or ""
  print(n .. suffix)
end
