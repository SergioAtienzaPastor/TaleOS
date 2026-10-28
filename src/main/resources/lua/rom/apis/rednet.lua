local rednet = {
  CHANNEL_BROADCAST = 65535,
}

local openModems = {}
local hosted = {}

local function checkProtocol(protocol)
  if protocol ~= nil and type(protocol) ~= "string" then
    error("protocol must be a string or nil", 3)
  end
end

local function envelope(message, protocol)
  return {
    talecraft_rednet = true,
    protocol = protocol,
    message = message,
  }
end

function rednet.isOpen(side)
  return openModems[side] ~= nil
end

function rednet.open(side)
  if openModems[side] then
    return true
  end
  if peripheral.getType(side) ~= "modem" then
    return false
  end
  local modem = peripheral.wrap(side)
  if not modem or not modem.isWireless() then
    return false
  end
  modem.open(os.getComputerID())
  modem.open(rednet.CHANNEL_BROADCAST)
  openModems[side] = modem
  return true
end

function rednet.close(side)
  local modem = openModems[side]
  if not modem then
    return
  end
  modem.close(os.getComputerID())
  modem.close(rednet.CHANNEL_BROADCAST)
  openModems[side] = nil
end

function rednet.closeAll()
  for side in pairs(openModems) do
    rednet.close(side)
  end
end

local function transmit(channel, message, protocol)
  local sent = false
  for _, modem in pairs(openModems) do
    modem.transmit(channel, os.getComputerID(), envelope(message, protocol))
    sent = true
  end
  return sent
end

function rednet.send(receiver, message, protocol)
  if type(receiver) ~= "number" or receiver % 1 ~= 0 or receiver < 0 or receiver > 65535 then
    error("receiver must be a channel between 0 and 65535", 2)
  end
  checkProtocol(protocol)
  return transmit(receiver, message, protocol)
end

function rednet.broadcast(message, protocol)
  checkProtocol(protocol)
  return transmit(rednet.CHANNEL_BROADCAST, message, protocol)
end

local function nextMessage(protocol, timer)
  while true do
    local event = table.pack(os.pullEventRaw())
    if event[1] == "timer" and event[2] == timer then
      return nil
    end
    if event[1] == "modem_message" and openModems[event[2]] then
      local sender, payload = event[4], event[5]
      if type(payload) == "table" and payload.talecraft_rednet == true
          and (protocol == nil or payload.protocol == protocol) then
        return sender, payload.message, payload.protocol
      end
    end
  end
end

function rednet.receive(protocol, timeout)
  checkProtocol(protocol)
  if timeout ~= nil and (type(timeout) ~= "number" or timeout < 0) then
    error("timeout must be a non-negative number or nil", 2)
  end
  local timer = timeout ~= nil and os.startTimer(timeout) or nil
  return nextMessage(protocol, timer)
end

function rednet.host(protocol, hostname)
  if type(protocol) ~= "string" or type(hostname) ~= "string" or hostname == "" then
    error("protocol and a non-empty hostname are required", 2)
  end
  if not next(openModems) then
    error("no open wireless modem", 2)
  end
  hosted[protocol] = hosted[protocol] or {}
  hosted[protocol][hostname] = true
end

function rednet.unhost(protocol)
  hosted[protocol] = nil
end

function rednet.lookup(protocol, hostname, timeout)
  if type(protocol) ~= "string" or (hostname ~= nil and type(hostname) ~= "string") then
    error("protocol must be a string and hostname a string or nil", 2)
  end
  rednet.broadcast({
    dns = "lookup",
    protocol = protocol,
    hostname = hostname,
  }, "dns")
  local timer = os.startTimer(timeout or 2)
  local results = {}
  local seen = {}
  while true do
    local sender, response = nextMessage("dns", timer)
    if sender == nil then
      break
    end
    if type(response) == "table" and response.dns == "lookup response"
        and response.protocol == protocol
        and (hostname == nil or response.hostname == hostname)
        and not seen[sender] then
      seen[sender] = true
      results[#results + 1] = sender
      if hostname ~= nil then
        os.cancelTimer(timer)
        return sender
      end
    end
  end
  return results
end

function rednet.run()
  while true do
    local event, side, _, sender, payload = os.pullEventRaw("modem_message")
    if event == "modem_message" and openModems[side] and type(payload) == "table"
        and payload.talecraft_rednet == true and payload.protocol == "dns"
        and type(payload.message) == "table" then
      local request = payload.message
      local names = hosted[request.protocol]
      if request.dns == "lookup" and names
          and (request.hostname == nil or names[request.hostname]) then
        for hostname in pairs(names) do
          if request.hostname == nil or request.hostname == hostname then
            rednet.send(sender, {
              dns = "lookup response",
              protocol = request.protocol,
              hostname = hostname,
            }, "dns")
          end
        end
      end
    end
  end
end

return rednet
