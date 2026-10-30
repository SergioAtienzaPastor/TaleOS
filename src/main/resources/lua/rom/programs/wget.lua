local url, path = ...
if not url then
  print(tale.tr("Usage: wget <url> [file]"))
  return
end
if type(http) ~= "table" or type(http.get) ~= "function" then
  print(tale.tr("wget: HTTP is disabled in mods/TaleOS/config.json"))
  return
end

if not path or path == "" then
  local clean = url:gsub("[?#].*$", "")
  path = clean:match("([^/]+)/?$") or "download"
end

print(tale.tr("Downloading ") .. url)
local response, err = http.get(url)
if not response then
  print("wget: " .. tostring(err))
  return
end

local code, reason = response.getResponseCode()
if code < 200 or code >= 300 then
  response.close()
  print("wget: HTTP " .. tostring(code) .. " " .. tostring(reason))
  return
end

local body = response.readAll()
response.close()
local ok, writeErr = pcall(fs.write, path, body)
if not ok then
  print("wget: " .. tale.tr("could not save ") .. path .. ": " .. tostring(writeErr))
  return
end
print(tale.tr("Saved ") .. path .. " (" .. tostring(#body) .. " bytes)")
