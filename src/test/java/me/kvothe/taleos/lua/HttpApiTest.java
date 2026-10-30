package me.kvothe.taleos.lua;

import me.kvothe.taleos.net.HttpBridge;
import me.kvothe.taleos.net.HttpResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpApiTest {
    private static final long BUDGET = 100_000;

    @Test
    void getSuspendsUntilFutureCompletesAndExposesResponse() {
        FakeHttpBridge bridge = new FakeHttpBridge(true);
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setHttpBridge(bridge);
        machine.load("print('before')\n"
                + "local response, err = http.get('https://example.test/data')\n"
                + "print('after', type(response), tostring(err))\n"
                + "print('body', response:readAll())\n"
                + "local code, reason = response:getResponseCode()\n"
                + "print('status', code, reason)\n"
                + "print('close', type(response.close), tostring(response:close()))\n", "http-success");

        machine.tick();

        assertEquals("before\n", machine.drainOutput());
        assertTrue(machine.isAlive());
        assertEquals(1, bridge.requests.size());
        Request request = bridge.requests.get(0);
        assertSame(machine, request.owner);
        assertEquals("https://example.test/data", request.url);

        machine.tick();
        assertEquals("", machine.drainOutput(), "an unfinished request must stay suspended");
        request.future.complete(new HttpResult(201, "Created", "respuesta"));
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("after\ttable\tnil\n"
                + "body\trespuesta\n"
                + "status\t201\tCreated\n"
                + "close\tfunction\tnil\n", machine.drainOutput());
    }

    @Test
    void exceptionalFutureReturnsNilAndMessage() {
        FakeHttpBridge bridge = new FakeHttpBridge(true);
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setHttpBridge(bridge);
        machine.load("local response, err = http.get('https://example.test/fail')\n"
                + "print(tostring(response), err)\n", "http-failure");

        machine.tick();
        bridge.requests.get(0).future.completeExceptionally(
                new CompletionException(new IllegalStateException("host denied")));
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("nil\thost denied\n", machine.drainOutput());
    }

    @Test
    void synchronousBridgeFailureAlsoReturnsNilAndMessage() {
        FakeHttpBridge bridge = new FakeHttpBridge(true);
        bridge.failure = new IllegalArgumentException("bad url");
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setHttpBridge(bridge);
        machine.load("local response, err = http.get('not a url')\n"
                + "print(tostring(response), err)\n", "http-sync-failure");

        machine.tick();
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("nil\tbad url\n", machine.drainOutput());
    }

    @Test
    void apiIsAbsentWithoutBridgeOrWhenDisabled() {
        LuaMachine withoutBridge = new LuaMachine(BUDGET);
        withoutBridge.load("print(type(http))", "http-null-default");
        withoutBridge.tick();
        assertEquals("nil\n", withoutBridge.drainOutput());

        LuaMachine nullBridge = new LuaMachine(BUDGET);
        nullBridge.setHttpBridge(null);
        nullBridge.load("print(type(http))", "http-null");
        nullBridge.tick();
        assertEquals("nil\n", nullBridge.drainOutput());

        LuaMachine disabled = new LuaMachine(BUDGET);
        disabled.setHttpBridge(new FakeHttpBridge(true));
        disabled.setHttpBridge(new FakeHttpBridge(false));
        disabled.load("print(type(http))", "http-disabled");
        disabled.tick();
        assertEquals("nil\n", disabled.drainOutput());
    }

    @Test
    void cancelPendingHttpDiscardsLateSuccess() {
        FakeHttpBridge bridge = new FakeHttpBridge(true);
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setHttpBridge(bridge);
        machine.load("local response = http.get('https://example.test/slow')\n"
                + "print('late', tostring(response))\n", "http-cancel");

        machine.tick();
        CompletableFuture<HttpResult> request = bridge.requests.get(0).future;
        machine.cancelPendingHttp();
        request.complete(new HttpResult(200, "OK", "too late"));
        machine.tick();

        assertTrue(machine.isAlive());
        assertEquals("", machine.drainOutput());
        assertNull(machine.getError());
        assertEquals(1, bridge.cancellations);
        assertSame(machine, bridge.lastCancelledOwner);
    }

    @Test
    void cancelPendingHttpDiscardsLateFailure() {
        FakeHttpBridge bridge = new FakeHttpBridge(true);
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setHttpBridge(bridge);
        machine.load("local response, err = http.get('https://example.test/slow')\n"
                + "print('late', tostring(response), tostring(err))\n", "http-cancel-failure");

        machine.tick();
        CompletableFuture<HttpResult> request = bridge.requests.get(0).future;
        machine.cancelPendingHttp();
        request.completeExceptionally(new CancellationException("cancelled"));
        machine.tick();

        assertTrue(machine.isAlive());
        assertEquals("", machine.drainOutput());
    }

    @Test
    void terminateCancelsRequestAndLateResponseCannotReviveProgram() {
        FakeHttpBridge bridge = new FakeHttpBridge(true);
        LuaMachine machine = new LuaMachine(BUDGET);
        machine.setHttpBridge(bridge);
        machine.load("tale.setProgramRunning(true)\n"
                + "local ok, err = pcall(function()\n"
                + "  local response = http.get('https://example.test/slow')\n"
                + "  print('late', tostring(response))\n"
                + "end)\n"
                + "tale.setProgramRunning(false)\n"
                + "print('terminated', tostring(ok), tostring(err))\n", "http-terminate");

        machine.tick();
        CompletableFuture<HttpResult> request = bridge.requests.get(0).future;
        machine.requestTerminate();
        machine.tick();

        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("terminated\tfalse\t" + LuaMachine.TERMINATED_MESSAGE + "\n", machine.drainOutput());
        assertEquals(1, bridge.cancellations);

        request.complete(new HttpResult(200, "OK", "too late"));
        machine.tick();
        assertEquals(LuaMachine.State.DONE, machine.getState());
        assertEquals("", machine.drainOutput());
    }

    private static final class FakeHttpBridge implements HttpBridge {
        private final boolean enabled;
        private final List<Request> requests = new ArrayList<>();
        private RuntimeException failure;
        private int cancellations;
        private Object lastCancelledOwner;

        private FakeHttpBridge(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public CompletableFuture<HttpResult> get(Object owner, String url) {
            if (failure != null) {
                throw failure;
            }
            Request request = new Request(owner, url);
            requests.add(request);
            return request.future;
        }

        @Override
        public void cancel(Object owner) {
            cancellations++;
            lastCancelledOwner = owner;
        }
    }

    private static final class Request {
        private final Object owner;
        private final String url;
        private final CompletableFuture<HttpResult> future = new CompletableFuture<>();

        private Request(Object owner, String url) {
            this.owner = owner;
            this.url = url;
        }
    }
}
