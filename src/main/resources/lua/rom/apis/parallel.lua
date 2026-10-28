local parallel = {}

local function run(waitForOne, ...)
  local tasks = {}
  local count = select("#", ...)
  for i = 1, count do
    local fn = select(i, ...)
    if type(fn) ~= "function" then
      error("bad argument #" .. i .. " (function expected)", 2)
    end
    tasks[i] = { co = coroutine.create(fn), filter = nil }
  end

  local remaining = count
  local event = table.pack()
  while remaining > 0 do
    for i = 1, count do
      local task = tasks[i]
      if task and (event.n == 0 or task.filter == nil or event[1] == task.filter) then
        local resumeArgs = event
        while task do
          local result = table.pack(coroutine.resume(task.co,
              table.unpack(resumeArgs, 1, resumeArgs.n)))
          if not result[1] then
            error(result[2], 0)
          end
          if coroutine.status(task.co) == "dead" then
            if waitForOne then
              return i
            end
            tasks[i] = nil
            remaining = remaining - 1
            task = nil
          elseif result[2] == "tale:wait_event" then
            task.filter = result[3]
            task = nil
          else
            resumeArgs = table.pack(coroutine.yield(table.unpack(result, 2, result.n)))
          end
        end
      end
    end
    if remaining > 0 then
      event = table.pack(os.pullEventRaw())
    end
  end
end

function parallel.waitForAny(...)
  return run(true, ...)
end

function parallel.waitForAll(...)
  run(false, ...)
end

return parallel
